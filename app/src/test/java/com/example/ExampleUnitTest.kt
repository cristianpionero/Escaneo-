package com.example

import com.example.util.CurrencyFormatter
import com.example.vision.VisualFingerprintExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCurrencyFormatterColombianPesos() {
        assertEquals("$ 0", CurrencyFormatter.formatCop(0L))
        assertEquals("$ 100", CurrencyFormatter.formatCop(100L))
        assertEquals("$ 1.000", CurrencyFormatter.formatCop(1000L))
        assertEquals("$ 2.500", CurrencyFormatter.formatCop(2500L))
        assertEquals("$ 10.000", CurrencyFormatter.formatCop(10000L))
        assertEquals("$ 150.000", CurrencyFormatter.formatCop(150000L))
    }

    @Test
    fun testCurrencyParser() {
        assertEquals(2500L, CurrencyFormatter.parseCopInput("$ 2.500"))
        assertEquals(10000L, CurrencyFormatter.parseCopInput("10.000"))
        assertEquals(0L, CurrencyFormatter.parseCopInput(""))
    }

    @Test
    fun testVectorSerialization() {
        val original = FloatArray(VisualFingerprintExtractor.EMBEDDING_DIM) { (it * 0.01).toFloat() }
        val serialized = VisualFingerprintExtractor.serialize(original)
        val deserialized = VisualFingerprintExtractor.deserialize(serialized)

        assertEquals(original.size, deserialized.size)
        val similarity = VisualFingerprintExtractor.cosineSimilarity(original, deserialized)
        assertTrue("Similarity should be > 0.999", similarity > 0.999f)
    }

    @Test
    fun testAverageVectors() {
        val v1 = FloatArray(128) { 1.0f }
        val v2 = FloatArray(128) { 1.0f }
        val avg = VisualFingerprintExtractor.averageVectors(listOf(v1, v2))

        assertEquals(128, avg.size)
        val sim = VisualFingerprintExtractor.cosineSimilarity(v1, avg)
        assertTrue("Similarity between identical normalized vectors should be ~1.0", sim > 0.99f)
    }
}
