package com.alarming.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ToneImportRulesTest {
    @Test fun supportedFormatsAreExplicit() {
        assertEquals(setOf("mp3", "wav", "m4a", "aac", "ogg", "flac"), ToneImportRules.extensions)
    }
    @Test fun boundedCopyPreservesBytes() {
        val bytes = ByteArray(5000) { (it % 127).toByte() }
        val result = ByteArrayOutputStream()
        ToneImportRules.copyBounded(ByteArrayInputStream(bytes), result)
        assertArrayEquals(bytes, result.toByteArray())
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedFileIsRejected() {
        ToneImportRules.copyBounded(ByteArrayInputStream(ByteArray(ToneImportRules.MAX_BYTES + 1)), ByteArrayOutputStream())
    }
    @Test(expected = IllegalArgumentException::class) fun emptyFileIsRejected() {
        ToneImportRules.copyBounded(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
    }
}