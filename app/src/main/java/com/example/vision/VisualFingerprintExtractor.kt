package com.example.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.local.ProductEntity
import kotlin.math.atan2
import kotlin.math.sqrt

data class RecognitionResult(
    val product: ProductEntity,
    val confidence: Float // e.g. 0.85f = 85%
)

/**
 * Motor de visión computacional y extracción de huellas dactilares visuales.
 * 
 * Genera un vector normalizado de 128 dimensiones combinando:
 * 1. Distribución Espacial de Color HSV en cuadrícula 4x4 (48 dims).
 * 2. Patrones de Textura Locales (LBP) en 4 cuadrantes (48 dims).
 * 3. Gradientes de Bordes y Contornos (HOG) (32 dims).
 */
object VisualFingerprintExtractor {

    const val EMBEDDING_DIM = 128
    const val MATCH_THRESHOLD = 0.60f

    /**
     * Recorta el Bitmap de la cámara para enfocar exactamente el área dentro del recuadro
     * del escáner (ScannerOverlay), descartando paredes, techos y bordes de la habitación.
     */
    fun cropToScanRegion(fullBitmap: Bitmap): Bitmap {
        val w = fullBitmap.width
        val h = fullBitmap.height
        if (w <= 64 || h <= 64) return fullBitmap

        val cropX = (w * 0.10f).toInt().coerceIn(0, w - 10)
        val cropY = (h * 0.16f).toInt().coerceIn(0, h - 10)
        val cropW = (w * 0.80f).toInt().coerceIn(10, w - cropX)
        val cropH = (h * 0.72f).toInt().coerceIn(10, h - cropY)

        return try {
            Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
        } catch (e: Exception) {
            fullBitmap
        }
    }

    /**
     * Comprueba si el fotograma capturado tiene información visual suficiente
     * (evita falsos disparos cuando la cámara está tapada, en negro o frente a una pared lisa).
     */
    fun isFrameActive(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) return false

        val stepX = (w / 8).coerceAtLeast(1)
        val stepY = (h / 8).coerceAtLeast(1)
        var totalBrightness = 0L
        var minLum = 255
        var maxLum = 0
        var count = 0

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                totalBrightness += lum
                if (lum < minLum) minLum = lum
                if (lum > maxLum) maxLum = lum
                count++
            }
        }
        if (count == 0) return false
        val avgBrightness = totalBrightness / count

        return avgBrightness in 18..248 && (maxLum - minLum) > 15
    }

    /**
     * Extrae un vector de 128 dimensiones a partir de un Bitmap.
     * Da prioridad al centro del recuadro donde el usuario sostiene el producto.
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val processedBitmap = if (bitmap.width > 120 && bitmap.height > 120) {
            cropToScanRegion(bitmap)
        } else {
            bitmap
        }

        val size = 64
        val scaled = Bitmap.createScaledBitmap(processedBitmap, size, size, true)
        val vector = FloatArray(EMBEDDING_DIM)

        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        // 1. Distribución Espacial de Color HSV en cuadrícula 4x4 (48 dimensiones)
        val cellW = size / 4
        val cellH = size / 4
        var vecIdx = 0

        for (cy in 0 until 4) {
            for (cx in 0 until 4) {
                val centerWeight = if (cx in 1..2 && cy in 1..2) 2.0f else 1.0f

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
                        sumH += (hsv[0] / 360f) * centerWeight
                        sumS += hsv[1] * centerWeight
                        sumV += hsv[2] * centerWeight
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

        // 2. Patrones de Textura Locales (LBP en 4 cuadrantes = 48 dimensiones)
        val gray = IntArray(size * size)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }

        val quadW = size / 2
        val quadH = size / 2

        for (qy in 0 until 2) {
            for (qx in 0 until 2) {
                val hist = FloatArray(12)
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

                val sum = hist.sum().coerceAtLeast(1f)
                for (k in 0 until 12) {
                    if (vecIdx < 96) {
                        vector[vecIdx++] = hist[k] / sum
                    }
                }
            }
        }

        // 3. Gradientes de Bordes y Contornos (HOG = 32 dimensiones restantes)
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

        normalizeL2(vector)
        return vector
    }

    /**
     * Similitud de Pearson (correlación de media cero).
     */
    fun pearsonSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f

        var mean1 = 0f
        var mean2 = 0f
        for (i in v1.indices) {
            mean1 += v1[i]
            mean2 += v2[i]
        }
        mean1 /= v1.size
        mean2 /= v2.size

        var dot = 0f
        var var1 = 0f
        var var2 = 0f

        for (i in v1.indices) {
            val d1 = v1[i] - mean1
            val d2 = v2[i] - mean2
            dot += d1 * d2
            var1 += d1 * d1
            var2 += d2 * d2
        }

        val denom = sqrt((var1 * var2).toDouble()).toFloat()
        if (denom < 1e-6f) return 0f

        val r = dot / denom
        return r.coerceIn(0f, 1f)
    }

    /**
     * Similitud Coseno estándar directa entre dos vectores.
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
     * Calcula similitud combinada (Pearson + Coseno) para balancear
     * discriminación de contraste y persistencia de tono de color.
     */
    fun combinedSimilarity(v1: FloatArray, v2: FloatArray): Float {
        val pearson = pearsonSimilarity(v1, v2)
        val cosine = cosineSimilarity(v1, v2)
        return (pearson * 0.65f + cosine * 0.35f).coerceIn(0f, 1f)
    }

    /**
     * Promedia los vectores de las fotos capturadas para crear el centroide del producto.
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
     * Serializa el vector a una cadena compacta para almacenamiento en Room SQLite.
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
     * Compara un vector de la cámara contra los productos que el usuario registró en su tienda.
     * Retorna el producto con mayor coincidencia si supera el umbral.
     */
    fun findBestMatch(
        queryVector: FloatArray,
        products: List<ProductEntity>,
        threshold: Float = MATCH_THRESHOLD
    ): RecognitionResult? {
        if (products.isEmpty()) return null

        var bestProduct: ProductEntity? = null
        var bestScore = -1f

        for (product in products) {
            val productVec = deserialize(product.featureVector)
            val score = combinedSimilarity(queryVector, productVec)
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
