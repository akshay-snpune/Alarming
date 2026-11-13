import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const sampleRate = 22_050;
const durationSeconds = 2;
const sampleCount = sampleRate * durationSeconds;
const outputDirectory = join(process.cwd(), 'app/src/main/res/raw');

function voice(frequency, start, amplitude, attack, hold, release, vibrato = 0) {
  return { frequency, start, amplitude, attack, hold, release, vibrato };
}

function bellPartials(fundamental, start, amplitude, release) {
  return [
    voice(fundamental, start, amplitude, 0.004, 0, release),
    voice(fundamental * 2.01, start, amplitude * 0.38, 0.004, 0, release * 1.22),
    voice(fundamental * 2.98, start, amplitude * 0.16, 0.004, 0, release * 1.48),
    voice(fundamental * 4.12, start, amplitude * 0.07, 0.004, 0, release * 1.9),
  ];
}

function sampleVoice(item, time) {
  const elapsed = time - item.start;
  if (elapsed < 0) return 0;

  let envelope;
  if (item.hold === 0) {
    envelope = Math.min(1, elapsed / item.attack) * Math.exp(-elapsed * item.release);
  } else if (elapsed < item.attack) {
    envelope = elapsed / item.attack;
  } else if (elapsed < item.attack + item.hold) {
    envelope = 1;
  } else {
    envelope = Math.exp(-(elapsed - item.attack - item.hold) * item.release);
  }

  const pitch = item.frequency + Math.sin(elapsed * Math.PI * 2 * 4.5) * item.vibrato;
  return Math.sin(elapsed * Math.PI * 2 * pitch) * item.amplitude * envelope;
}

function encodeWav(voices, noiseBursts = []) {
  const samples = new Float32Array(sampleCount);
  let peak = 0;
  // Timer cues must sound on the boundary, so the first onset is moved to sample 0.
  const leadIn = Math.min(...voices.map((item) => item.start), ...noiseBursts.map((burst) => burst.start));

  for (let index = 0; index < sampleCount; index += 1) {
    const time = index / sampleRate + leadIn;
    let value = 0;
    for (const item of voices) value += sampleVoice(item, time);

    for (const burst of noiseBursts) {
      const elapsed = time - burst.start;
      if (elapsed >= 0 && elapsed < burst.length) {
        const envelope = Math.exp(-elapsed * burst.decay);
        const noise = Math.sin(index * 12.9898 + burst.seed) * 0.55 + Math.sin(index * 78.233 + burst.seed * 2) * 0.45;
        value += noise * envelope * burst.amplitude;
      }
    }

    samples[index] = value;
    peak = Math.max(peak, Math.abs(value));
  }

  const dataSize = sampleCount * 2;
  const wav = Buffer.alloc(44 + dataSize);
  wav.write('RIFF', 0);
  wav.writeUInt32LE(36 + dataSize, 4);
  wav.write('WAVE', 8);
  wav.write('fmt ', 12);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(sampleRate, 24);
  wav.writeUInt32LE(sampleRate * 2, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write('data', 36);
  wav.writeUInt32LE(dataSize, 40);

  const gain = peak > 0 ? Math.min(0.92 / peak, 1.8) : 1;
  for (let index = 0; index < sampleCount; index += 1) {
    const value = Math.max(-1, Math.min(1, samples[index] * gain));
    wav.writeInt16LE(Math.round(value * 32767), 44 + index * 2);
  }

  return wav;
}

const tones = [
  [
    'retro_pager',
    [0.08, 0.42, 0.92, 1.26].flatMap((start) => [voice(830.61, start, 0.24, 0.008, 0.16, 25), voice(1661.22, start, 0.04, 0.008, 0.14, 25)]),
  ],
  [
    'keypad_cascade',
    [587.33, 783.99, 554.37, 739.99, 622.25, 830.61].map((frequency, index) => voice(frequency, 0.08 + index * 0.27, 0.24, 0.008, 0.15, 25)),
  ],
  [
    'pocket_pulse',
    [0.06, 0.3, 0.72, 0.96, 1.38, 1.62].flatMap((start) => [voice(480, start, 0.2, 0.008, 0.12, 25), voice(620, start, 0.16, 0.008, 0.12, 25)]),
  ],
  ['soft_bell', [...bellPartials(659.3, 0.06, 0.38, 2.2)]],
  ['digital_beep', [voice(660, 0.08, 0.28, 0.012, 0.28, 18), voice(880, 0.52, 0.25, 0.012, 0.3, 18), voice(740, 1.02, 0.24, 0.012, 0.4, 17)]],
  [
    'classic_tick',
    [voice(1_650, 0.08, 0.16, 0.001, 0, 45), voice(1_320, 0.6, 0.18, 0.001, 0, 42), voice(1_650, 1.12, 0.16, 0.001, 0, 45)],
    [
      { start: 0.08, length: 0.018, decay: 90, amplitude: 0.16, seed: 1 },
      { start: 0.6, length: 0.018, decay: 90, amplitude: 0.18, seed: 2 },
      { start: 1.12, length: 0.018, decay: 90, amplitude: 0.16, seed: 3 },
    ],
  ],
  ['double_bell', [...bellPartials(784, 0.06, 0.34, 3.2), ...bellPartials(988, 0.62, 0.28, 3.4)]],
  [
    'temple_bell',
    [
      voice(392, 0.05, 0.38, 0.006, 0, 1.45),
      voice(527, 0.05, 0.22, 0.006, 0, 1.75),
      voice(785, 0.05, 0.18, 0.006, 0, 2.1),
      voice(1_176, 0.05, 0.11, 0.006, 0, 2.45),
      voice(1_570, 0.05, 0.06, 0.006, 0, 2.8),
    ],
  ],
  [
    'om_hum',
    [
      voice(136.1, 0.06, 0.34, 0.42, 0.72, 1.55, 1.1),
      voice(272.2, 0.06, 0.15, 0.44, 0.66, 2.0, 1.4),
      voice(408.3, 0.06, 0.07, 0.48, 0.58, 2.3, 1.8),
      voice(544.4, 0.06, 0.035, 0.5, 0.56, 2.6, 2.1),
    ],
  ],
  [
    'focus_tone',
    [voice(523.25, 0.05, 0.22, 0.18, 0.24, 3.8, 0.8), voice(659.25, 0.43, 0.22, 0.2, 0.32, 3.7, 0.8), voice(783.99, 0.98, 0.2, 0.22, 0.34, 3.8, 0.8)],
  ],
  [
    'completion_chime',
    [
      ...bellPartials(523.25, 0.04, 0.25, 3.0),
      ...bellPartials(659.25, 0.48, 0.22, 3.2),
      ...bellPartials(783.99, 0.94, 0.24, 3.4),
      ...bellPartials(1_046.5, 1.38, 0.22, 3.6),
    ],
  ],
  [
    'retro_dual_tone',
    [
      voice(440, 0.06, 0.2, 0.012, 0.2, 17),
      voice(554.37, 0.06, 0.18, 0.012, 0.2, 17),
      voice(440, 0.52, 0.2, 0.012, 0.2, 17),
      voice(554.37, 0.52, 0.18, 0.012, 0.2, 17),
      voice(440, 1.02, 0.2, 0.012, 0.2, 17),
      voice(554.37, 1.02, 0.18, 0.012, 0.2, 17),
      voice(440, 1.5, 0.2, 0.012, 0.2, 17),
      voice(554.37, 1.5, 0.18, 0.012, 0.2, 17),
    ],
  ],
  [
    'pocket_polyphony',
    [
      voice(698.46, 0.06, 0.24, 0.015, 0.17, 22),
      voice(880, 0.35, 0.22, 0.015, 0.17, 22),
      voice(659.25, 0.65, 0.23, 0.015, 0.18, 21),
      voice(987.77, 0.96, 0.21, 0.015, 0.18, 21),
      voice(739.99, 1.28, 0.22, 0.015, 0.2, 20),
      voice(587.33, 1.62, 0.2, 0.015, 0.23, 20),
    ],
  ],
  [
    'mobile_chirp',
    [
      voice(1_046.5, 0.06, 0.2, 0.008, 0.1, 25),
      voice(784, 0.2, 0.19, 0.008, 0.12, 24),
      voice(932.33, 0.48, 0.21, 0.008, 0.11, 24),
      voice(698.46, 0.63, 0.19, 0.008, 0.13, 23),
      voice(1_174.66, 0.96, 0.2, 0.008, 0.12, 24),
      voice(880, 1.12, 0.19, 0.008, 0.13, 23),
      voice(1_046.5, 1.48, 0.2, 0.008, 0.12, 24),
      voice(784, 1.64, 0.19, 0.008, 0.18, 22),
    ],
  ],
];

mkdirSync(outputDirectory, { recursive: true });
for (const [name, voices, noise] of tones) {
  writeFileSync(join(outputDirectory, `${name}.wav`), encodeWav(voices, noise));
}

console.log(`Generated ${tones.length} original ${durationSeconds}-second tones in ${outputDirectory}`);
