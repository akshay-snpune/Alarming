package com.alarming.feature.reminders

import com.alarming.R

data class ReminderTone(
    val name: String,
    val resourceId: Int,
    val description: String = "Original two-second tone",
    val id: String = name,
)

object ReminderTones {
    val protectedIds = setOf("Soft bell", "Focus tone", "Completion chime", "collection:temple_bell", "collection:aarti_bells")

    val all = listOf(
        ReminderTone("Soft bell", R.raw.soft_bell),
        ReminderTone("Digital beep", R.raw.digital_beep),
        ReminderTone("Classic tick", R.raw.classic_tick),
        ReminderTone("Double bell", R.raw.double_bell),
        ReminderTone("Temple bell", R.raw.temple_bell),
        ReminderTone("Om hum", R.raw.om_hum),
        ReminderTone("Focus tone", R.raw.focus_tone),
        ReminderTone("Completion chime", R.raw.completion_chime),
        ReminderTone("Retro dual-tone", R.raw.retro_dual_tone),
        ReminderTone("Pocket polyphony", R.raw.pocket_polyphony),
        ReminderTone("Mobile chirp", R.raw.mobile_chirp),
        ReminderTone("Retro pager", R.raw.retro_pager, "Bright paired pager beeps"),
        ReminderTone("Keypad cascade", R.raw.keypad_cascade, "An original six-note mobile motif"),
        ReminderTone("Pocket pulse", R.raw.pocket_pulse, "Three pairs of classic dual-frequency rings"),
        ReminderTone("Number one", R.raw.collection_number_one, "Supplied tone", "collection:number_one"),
        ReminderTone("Number two", R.raw.collection_number_two, "Supplied tone", "collection:number_two"),
        ReminderTone("Number three", R.raw.collection_number_three, "Supplied tone", "collection:number_three"),
        ReminderTone("Number four", R.raw.collection_number_four, "Supplied tone", "collection:number_four"),
        ReminderTone("Number five", R.raw.collection_number_five, "Supplied tone", "collection:number_five"),
        ReminderTone("Number six", R.raw.collection_number_six, "Supplied tone", "collection:number_six"),
        ReminderTone("Number seven", R.raw.collection_number_seven, "Supplied tone", "collection:number_seven"),
        ReminderTone("Number eight", R.raw.collection_number_eight, "Supplied tone", "collection:number_eight"),
        ReminderTone("Number nine", R.raw.collection_number_nine, "Supplied tone", "collection:number_nine"),
        ReminderTone("Number ten", R.raw.collection_number_ten, "Supplied tone", "collection:number_ten"),
        ReminderTone("Temple bell (collection)", R.raw.collection_temple_bell, "Supplied tone", "collection:temple_bell"),
        ReminderTone("Om resonance instrumental", R.raw.collection_om_resonance_instrumental, "Supplied tone", "collection:om_resonance_instrumental"),
        ReminderTone("Aarti bells", R.raw.collection_aarti_bells, "Supplied tone", "collection:aarti_bells"),
        ReminderTone("Shankh naad synth", R.raw.collection_shankh_naad_synth, "Supplied tone", "collection:shankh_naad_synth"),
        ReminderTone("Shiva damru rhythm", R.raw.collection_shiva_damru_rhythm, "Supplied tone", "collection:shiva_damru_rhythm"),
        ReminderTone("Om namah shivaya synth voice", R.raw.collection_om_namah_shivaya_synth_voice, "Supplied tone", "collection:om_namah_shivaya_synth_voice"),
        ReminderTone("Layered temple bells", R.raw.collection_layered_temple_bells, "Supplied tone", "collection:layered_temple_bells"),
        ReminderTone("Singing bowl resonance", R.raw.collection_singing_bowl_resonance, "Supplied tone", "collection:singing_bowl_resonance"),
        ReminderTone("Vedic mantra drone", R.raw.collection_vedic_mantra_drone, "Supplied tone", "collection:vedic_mantra_drone"),
        ReminderTone("Shiva temple atmosphere synth", R.raw.collection_shiva_temple_atmosphere_synth, "Supplied tone", "collection:shiva_temple_atmosphere_synth"),
    )
}