package com.example.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.local.ProductEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RecognitionResult(
    val product: ProductEntity,
    val confidence: Float // e.g. 0.94f = 94%
)

/**
 * Motor de extracción de huellas dactilares visuales (Feature Embeddings).
 * Genera un vector normalizado de 128 dimensiones por cada ángulo de producto,
 * combinando distribución espacial de color HSV, gradientes de bordes (HOG)
 * y patrones de textura locales (LBP).
 */
object VisualFingerprintExtractor {

    const val EMBEDDING_DIM = 128
    const val MATCH_THRESHOLD = 0.82f // 82% umbral estricto para evitar falsos positivos

    /**
     * Extrae un vector de 128 dimensiones a partir de un Bitmap recortado del producto.
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val size = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val vector = FloatArray(EMBEDDING_DIM)

        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        // 1. Distribución Espacial de Color HSV en cuadrícula 4x4 (16 celdas x 3 = 48 dimensiones)
        val cellW = size / 4
        val cellH = size / 4
        var vecIdx = 0

        for (cy in 0 until 4) {
            for (cx in 0 until 4) {
                var sumH = 0f
                var sumS = 0f
                var sumV = 0f
                var count = 0

                for (y in (cy * cellH) until ((cy + 1) * cellH)) {
                    for (x in (cx * cellW) until ((cx + 1) * cellW)) {
                        val p = pixels[y * size + x]
                        val r = Color.red(p)
                        val g = Color.green(p)
                        val b = Color.blue(p)

                        val hsv = FloatArray(3)
                        Color.RGBToHSV(r, g, b, hsv)
                        sumH += hsv[0] / 360f // Normalizado 0..1
                        sumS += hsv[1]
                        sumV += hsv[2]
                        count++
                    }
                }

                if (count > 0) {
                    vector[vecIdx++] = sumH / count
                    vector[vecIdx++] = sumS / count
                    vector[vecIdx++] = sumV / count
                }
            }
        }

        // 2. Patrones de Textura Locales (LBP simplificado en 4 cuadrantes = 48 dimensiones)
        val gray = IntArray(size * size)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }

        val quadW = size / 2
        val quadH = size / 2

        for (qy in 0 until 2) {
            for (qx in 0 until 2) {
                val hist = FloatArray(12) // 12 bins de textura por cuadrante = 48 dimensiones
                for (y in (qy * quadH + 1) until ((qy + 1) * quadH - 1)) {
                    for (x in (qx * quadW + 1) until ((qx + 1) * quadW - 1)) {
                        val center = gray[y * size + x]
                        var code = 0
                        if (gray[(y - 1) * size + (x - 1)] >= center) code = code or 1
                        if (gray[(y - 1) * size + x] >= center) code = code or 2
                        if (gray[(y - 1) * size + (x + 1)] >= center) code = code or 4
                        if (gray[y * size + (x + 1)] >= center) code = code or 8
                        if (gray[(y + 1) * size + (x + 1)] >= center) code = code or 16
                        if (gray[(y + 1) * size + x] >= center) code = code or 32
                        if (gray[(y + 1) * size + (x - 1)] >= center) code = code or 64
                        if (gray[y * size + (x - 1)] >= center) code = code or 128

                        val bin = (code * 12) / 256
                        if (bin in 0..11) {
                            hist[bin] += 1f
                        }
                    }
                }

                // Normalizar histograma de cuadrante y copiar
                val sum = hist.sum().coerceAtLeast(1f)
                for (k in 0 until 12) {
                    if (vecIdx < 96) {
                        vector[vecIdx++] = hist[k] / sum
                    }
                }
            }
        }

        // 3. Gradientes de Bordes y Contornos (HOG simplificado = 32 dimensiones restantes)
        // 4 cuadrantes x 8 bins de orientación
        for (qy in 0 until 2) {
            for (qx in 0 until 2) {
                val hog = FloatArray(8)
                for (y in (qy * quadH + 1) until ((qy + 1) * quadH - 1)) {
                    for (x in (qx * quadW + 1) until ((qx + 1) * quadW - 1)) {
                        val dx = gray[y * size + (x + 1)] - gray[y * size + (x - 1)]
                        val dy = gray[(y + 1) * size + x] - gray[(y - 1) * size + x]
                        val mag = sqrt((dx * dx + dy * dy).toFloat())

                        var angle = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / Math.PI)
                        if (angle < 0) angle += 180.0

                        val bin = ((angle / 180.0) * 8).toInt().coerceIn(0, 7)
                        hog[bin] += mag
                    }
                }

                val sumMag = hog.sum().coerceAtLeast(1f)
                for (b in 0 until 8) {
                    if (vecIdx < EMBEDDING_DIM) {
                        vector[vecIdx++] = hog[b] / sumMag
                    }
                }
            }
        }

        // L2 Normalization del vector completo (para que el producto punto sea directamente Similitud Coseno)
        normalizeL2(vector)
        return vector
    }

    /**
     * Calcula la Similitud Coseno entre dos vectores (entre 0.0 y 1.0).
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dot = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(0f, 1f)
    }

    /**
     * Promedia las huellas dactilares de las 20 fotos capturadas
     * para crear el vector representativo (centroide) del producto.
     */
    fun averageVectors(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(EMBEDDING_DIM)
        val centroid = FloatArray(EMBEDDING_DIM)

        for (v in vectors) {
            for (i in v.indices) {
                centroid[i] += v[i]
            }
        }

        val n = vectors.size.toFloat()
        for (i in centroid.indices) {
            centroid[i] /= n
        }

        normalizeL2(centroid)
        return centroid
    }

    private fun normalizeL2(v: FloatArray) {
        var sumSquares = 0f
        for (x in v) {
            sumSquares += x * x
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat().coerceAtLeast(1e-6f)
        for (i in v.indices) {
            v[i] /= norm
        }
    }

    /**
     * Serializa el vector a una cadena compacta separada por comas para Room SQLite.
     */
    fun serialize(vector: FloatArray): String {
        return vector.joinToString(separator = ",") { "%.4f".format(java.util.Locale.US, it) }
    }

    /**
     * Deserializa la cadena a FloatArray.
     */
    fun deserialize(str: String): FloatArray {
        if (str.isBlank()) return FloatArray(EMBEDDING_DIM)
        val parts = str.split(",")
        val vector = FloatArray(parts.size)
        for (i in parts.indices) {
            vector[i] = parts[i].trim().toFloatOrNull() ?: 0f
        }
        return vector
    }

    /**
     * Compara un vector de la cámara contra la lista de productos registrados en la base de datos.
     * Retorna el producto con mayor coincidencia si supera el umbral estricto.
     */
    fun findBestMatch(
        queryVector: FloatArray,
        products: List<ProductEntity>,
        threshold: Float = MATCH_THRESHOLD
    ): RecognitionResult? {
        if (products.isEmpty()) return null

        var bestProduct: ProductEntity? = null
        var bestScore = 0f

        for (product in products) {
            val productVec = deserialize(product.featureVector)
            val score = cosineSimilarity(queryVector, productVec)
            if (score > bestScore) {
                bestScore = score
                bestProduct = product
            }
        }

        return if (bestProduct != null && bestScore >= threshold) {
            RecognitionResult(bestProduct, bestScore)
        } else {
            null
        }
    }
}
