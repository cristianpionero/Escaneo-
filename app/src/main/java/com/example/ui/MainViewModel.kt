package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProductEntity
import com.example.data.local.ProductRepository
import com.example.util.CurrencyFormatter
import com.example.vision.RecognitionResult
import com.example.vision.VisualFingerprintExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MappingProgress(
    val isPreparingCountdown: Boolean = false,
    val countdownNumber: Int = 3, // 3, 2, 1
    val isRunning: Boolean = false,
    val photosCaptured: Int = 0,
    val maxPhotos: Int = 20,
    val justTookPhoto: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    val allProducts: StateFlow<List<ProductEntity>>

    // Contador de dinero acumulado en la calculadora
    private val _totalMoney = MutableStateFlow(0L)
    val totalMoney: StateFlow<Long> = _totalMoney.asStateFlow()

    // Estado del mapeo guiado
    private val _mappingProgress = MutableStateFlow(MappingProgress())
    val mappingProgress: StateFlow<MappingProgress> = _mappingProgress.asStateFlow()

    // Vectores capturados durante el mapeo de 20 fotos
    private val capturedVectors = mutableListOf<FloatArray>()
    private var mappingJob: Job? = null

    // Campos de configuración de precio y nombre para los productos a registrar
    var nextProductName = MutableStateFlow("")
        private set
    var nextProductPriceCop = MutableStateFlow("$ 2.000")
        private set

    // Último producto reconocido en escáner
    private val _lastRecognition = MutableStateFlow<RecognitionResult?>(null)
    val lastRecognition: StateFlow<RecognitionResult?> = _lastRecognition.asStateFlow()

    // LÓGICA ESTRICTA SOLICITADA:
    // El mismo producto se cuenta una sola vez y no vuelve a sumar a menos que
    // se pase un producto DIFERENTE.
    private var lastScannedProductId: Long? = null

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ProductRepository(db.productDao())
        allProducts = repository.allProducts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun onNextNameChange(name: String) {
        nextProductName.value = name
    }

    fun onNextPriceChange(rawText: String) {
        val parsed = CurrencyFormatter.parseCopInput(rawText)
        nextProductPriceCop.value = if (parsed == 0L && rawText.isBlank()) "" else CurrencyFormatter.formatCop(parsed)
    }

    /**
     * Inicia automáticamente el modo de registro:
     * 1. Contador transparente pequeño 3, 2, 1.
     * 2. Rastreo de 20 imágenes (1 foto cada 2 segundos).
     * 3. Al terminar las 20, guarda automáticamente en la base de datos y pasa a modo escáner azul.
     */
    fun startAutoRegistration(
        captureFrame: () -> Bitmap?,
        onPhotoTakenSound: () -> Unit,
        onSuccessSaved: () -> Unit
    ) {
        mappingJob?.cancel()
        capturedVectors.clear()

        mappingJob = viewModelScope.launch {
            // Fase 1: Contador transparente pequeño 3, 2, 1 (da 3 segundos para acomodar el producto)
            _mappingProgress.value = MappingProgress(
                isPreparingCountdown = true,
                countdownNumber = 3,
                isRunning = false
            )
            for (count in 3 downTo 1) {
                _mappingProgress.value = _mappingProgress.value.copy(countdownNumber = count)
                delay(1000)
            }

            // Fase 2: Inicia rastreo de 20 fotos (1 cada 2 segundos)
            _mappingProgress.value = MappingProgress(
                isPreparingCountdown = false,
                isRunning = true,
                photosCaptured = 0,
                maxPhotos = 20
            )

            var photosCount = 0
            for (step in 1..20) {
                // Espera 2 segundos entre foto y foto
                delay(2000)

                val frame = captureFrame()
                val vector = if (frame != null) {
                    VisualFingerprintExtractor.extractEmbedding(frame)
                } else {
                    FloatArray(VisualFingerprintExtractor.EMBEDDING_DIM) { 0.1f }
                }
                capturedVectors.add(vector)
                photosCount++

                onPhotoTakenSound()

                // Destello visual sutil
                _mappingProgress.value = _mappingProgress.value.copy(
                    photosCaptured = photosCount,
                    justTookPhoto = true
                )
                delay(120)
                _mappingProgress.value = _mappingProgress.value.copy(justTookPhoto = false)
            }

            // Fase 3: Auto-guardado de la huella dactilar de 128 dimensiones en la base de datos
            val currentCount = allProducts.value.size + 1
            val name = nextProductName.value.trim().ifBlank { "Producto $currentCount" }
            val parsedPrice = CurrencyFormatter.parseCopInput(nextProductPriceCop.value)
            val finalPrice = if (parsedPrice <= 0L) 2000L else parsedPrice

            val centroidVector = if (capturedVectors.isNotEmpty()) {
                VisualFingerprintExtractor.averageVectors(capturedVectors)
            } else {
                FloatArray(VisualFingerprintExtractor.EMBEDDING_DIM) { 0.5f }
            }

            val entity = ProductEntity(
                name = name,
                price = finalPrice,
                sampleCount = 20,
                featureVector = VisualFingerprintExtractor.serialize(centroidVector)
            )
            repository.insertProduct(entity)

            // Limpia progreso y llama éxito
            capturedVectors.clear()
            _mappingProgress.value = MappingProgress()
            nextProductName.value = ""

            onSuccessSaved()
        }
    }

    fun cancelAutoRegistration() {
        mappingJob?.cancel()
        capturedVectors.clear()
        _mappingProgress.value = MappingProgress()
    }

    /**
     * Procesa un fotograma en Modo Escáner (Azul).
     * REGLA ESTRICTA: El mismo producto se cuenta una sola vez y no vuelve a sumar
     * a menos que pase un producto DIFERENTE.
     */
    fun processScanFrame(
        frame: Bitmap,
        onRecognized: (RecognitionResult) -> Unit
    ) {
        val currentProducts = allProducts.value
        if (currentProducts.isEmpty()) return

        val queryVector = VisualFingerprintExtractor.extractEmbedding(frame)
        val match = VisualFingerprintExtractor.findBestMatch(queryVector, currentProducts)

        if (match != null) {
            // Si es el mismo producto recién escaneado, el sistema ni se inmuta
            if (match.product.id != lastScannedProductId) {
                lastScannedProductId = match.product.id

                _totalMoney.value += match.product.price
                _lastRecognition.value = match

                onRecognized(match)

                // Limpia el badge tras 2.5s para no ensuciar la pantalla
                viewModelScope.launch {
                    delay(2500)
                    if (_lastRecognition.value == match) {
                        _lastRecognition.value = null
                    }
                }
            }
        }
    }

    fun resetMoney() {
        _totalMoney.value = 0L
        lastScannedProductId = null
        _lastRecognition.value = null
    }

    fun addManualTestScan(product: ProductEntity, onRecognized: (RecognitionResult) -> Unit) {
        // En simulación manual también respeta la regla de producto diferente
        if (product.id != lastScannedProductId) {
            lastScannedProductId = product.id
            _totalMoney.value += product.price
            val res = RecognitionResult(product, 0.95f)
            _lastRecognition.value = res
            onRecognized(res)
        }
    }

    fun deleteProduct(id: Long) {
        viewModelScope.launch {
            repository.deleteProductById(id)
            if (lastScannedProductId == id) {
                lastScannedProductId = null
            }
        }
    }
}
