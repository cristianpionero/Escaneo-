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
    val maxPhotos: Int = 15,
    val justTookPhoto: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    // Catálogo 100% de productos reales del usuario en SQLite
    val allProducts: StateFlow<List<ProductEntity>>

    // Total acumulado en la calculadora
    private val _totalMoney = MutableStateFlow(0L)
    val totalMoney: StateFlow<Long> = _totalMoney.asStateFlow()

    // Progreso del mapeo de fotos
    private val _mappingProgress = MutableStateFlow(MappingProgress())
    val mappingProgress: StateFlow<MappingProgress> = _mappingProgress.asStateFlow()

    // Estado cuando termina de tomar las 15 fotos y espera a que el usuario escriba nombre y precio
    private val _isWaitingForDetails = MutableStateFlow(false)
    val isWaitingForDetails: StateFlow<Boolean> = _isWaitingForDetails.asStateFlow()

    // Huella vectorial pendiente por guardar
    private var pendingCentroidVector: FloatArray? = null

    // Campos para nombre y precio ingresados por el usuario
    var inputProductName = MutableStateFlow("")
        private set
    var inputProductPrice = MutableStateFlow("")
        private set

    // Último producto reconocido en escáner
    private val _lastRecognition = MutableStateFlow<RecognitionResult?>(null)
    val lastRecognition: StateFlow<RecognitionResult?> = _lastRecognition.asStateFlow()

    // Mensaje de estado en pantalla
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val capturedVectors = mutableListOf<FloatArray>()
    private var mappingJob: Job? = null
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

    fun onInputNameChange(name: String) {
        inputProductName.value = name
    }

    fun onInputPriceChange(priceRaw: String) {
        inputProductPrice.value = priceRaw.filter { it.isDigit() }
    }

    /**
     * Inicia el modo de registro:
     * 1. Contador transparente 3, 2, 1 (3 segundos).
     * 2. Toma 15 fotos de la región del recuadro láser.
     * 3. Al terminar NO guarda automáticamente, sino que activa los recuadros
     *    en la parte inferior para que el usuario escriba el nombre y el precio.
     */
    fun startAutoRegistration(
        captureFrame: () -> Bitmap?,
        onPhotoTakenSound: () -> Unit,
        onMappingFinished: () -> Unit
    ) {
        mappingJob?.cancel()
        capturedVectors.clear()
        _isWaitingForDetails.value = false
        pendingCentroidVector = null
        inputProductName.value = ""
        inputProductPrice.value = ""

        mappingJob = viewModelScope.launch {
            // Fase 1: Contador 3, 2, 1
            _mappingProgress.value = MappingProgress(
                isPreparingCountdown = true,
                countdownNumber = 3,
                isRunning = false
            )
            for (count in 3 downTo 1) {
                _mappingProgress.value = _mappingProgress.value.copy(countdownNumber = count)
                delay(950)
            }

            // Fase 2: Captura de fotos
            val totalSteps = 15
            _mappingProgress.value = MappingProgress(
                isPreparingCountdown = false,
                isRunning = true,
                photosCaptured = 0,
                maxPhotos = totalSteps
            )

            var photosCount = 0
            for (step in 1..totalSteps) {
                delay(1100)

                val rawFrame = captureFrame()
                val vector = if (rawFrame != null) {
                    val cropped = VisualFingerprintExtractor.cropToScanRegion(rawFrame)
                    VisualFingerprintExtractor.extractEmbedding(cropped)
                } else {
                    FloatArray(VisualFingerprintExtractor.EMBEDDING_DIM) { 0.1f }
                }
                capturedVectors.add(vector)
                photosCount++

                onPhotoTakenSound()

                _mappingProgress.value = _mappingProgress.value.copy(
                    photosCaptured = photosCount,
                    justTookPhoto = true
                )
                delay(100)
                _mappingProgress.value = _mappingProgress.value.copy(justTookPhoto = false)
            }

            // Fase 3: Promedio del centroide
            pendingCentroidVector = if (capturedVectors.isNotEmpty()) {
                VisualFingerprintExtractor.averageVectors(capturedVectors)
            } else {
                FloatArray(VisualFingerprintExtractor.EMBEDDING_DIM) { 0.5f }
            }

            // Finaliza la captura de fotos y habilita los recuadros de texto abajo
            _mappingProgress.value = MappingProgress()
            _isWaitingForDetails.value = true
            onMappingFinished()
        }
    }

    /**
     * Guarda en la base de datos SQLite el producto capturado con el nombre y precio
     * especificados por el usuario en los recuadros inferiores.
     */
    fun confirmSaveProduct(
        onSuccessSaved: () -> Unit
    ) {
        val vector = pendingCentroidVector ?: return
        val currentCount = allProducts.value.size + 1
        val name = inputProductName.value.trim().ifBlank { "Producto $currentCount" }
        val priceLong = inputProductPrice.value.toLongOrNull() ?: 2000L

        viewModelScope.launch {
            val entity = ProductEntity(
                name = name,
                price = priceLong,
                sampleCount = 15,
                featureVector = VisualFingerprintExtractor.serialize(vector)
            )
            repository.insertProduct(entity)

            // Limpieza de estado
            capturedVectors.clear()
            pendingCentroidVector = null
            _isWaitingForDetails.value = false
            inputProductName.value = ""
            inputProductPrice.value = ""

            _userMessage.value = "¡$name guardado en tu base de datos (${CurrencyFormatter.formatCop(priceLong)})!"
            onSuccessSaved()

            delay(3000)
            if (_userMessage.value?.startsWith("¡") == true) {
                _userMessage.value = null
            }
        }
    }

    fun cancelRegistration() {
        mappingJob?.cancel()
        capturedVectors.clear()
        pendingCentroidVector = null
        _mappingProgress.value = MappingProgress()
        _isWaitingForDetails.value = false
        inputProductName.value = ""
        inputProductPrice.value = ""
    }

    /**
     * Escaneo de productos en tiempo real (Modo Azul).
     * Solo suma dinero cuando se detecta un producto diferente al anterior.
     */
    fun processScanFrame(
        frame: Bitmap,
        onRecognized: (RecognitionResult) -> Unit
    ) {
        val currentProducts = allProducts.value
        if (currentProducts.isEmpty() || _isWaitingForDetails.value) return

        val cropped = VisualFingerprintExtractor.cropToScanRegion(frame)
        if (!VisualFingerprintExtractor.isFrameActive(cropped)) return

        val queryVector = VisualFingerprintExtractor.extractEmbedding(cropped)
        val match = VisualFingerprintExtractor.findBestMatch(queryVector, currentProducts)

        if (match != null) {
            if (match.product.id != lastScannedProductId) {
                lastScannedProductId = match.product.id

                _totalMoney.value += match.product.price
                _lastRecognition.value = match

                onRecognized(match)

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

    fun deleteProduct(id: Long) {
        viewModelScope.launch {
            repository.deleteProductById(id)
            if (lastScannedProductId == id) {
                lastScannedProductId = null
            }
        }
    }
}
