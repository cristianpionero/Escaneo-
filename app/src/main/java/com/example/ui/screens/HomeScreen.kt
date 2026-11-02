package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.ColombiaFlag
import com.example.ui.components.ScannerOverlay
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.delay

enum class AppMode {
    SCAN, // Modo normal de escaneo / cobro (Azul)
    REGISTER // Modo registrar producto / mapeo 20 fotos (Rojo)
}

class ScannerFeedbackController(private val context: Context) {
    private val toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: Exception) {
        null
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun playSupermarketBeep() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCameraShutterSound() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 35)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerModeSwitchHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            toneGen?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val feedbackController = remember(context) { ScannerFeedbackController(context) }
    DisposableEffect(feedbackController) {
        onDispose {
            feedbackController.release()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraSelectorFacing by remember {
        mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
    }

    var currentMode by remember {
        mutableStateOf(AppMode.SCAN)
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showProductCatalogDialog by remember { mutableStateOf(false) }

    val totalMoney by viewModel.totalMoney.collectAsStateWithLifecycle()
    val mappingProgress by viewModel.mappingProgress.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val lastRecognition by viewModel.lastRecognition.collectAsStateWithLifecycle()
    val nextProductPriceCop by viewModel.nextProductPriceCop.collectAsStateWithLifecycle()

    // Bucle continuo de escaneo en segundo plano en Modo Escaneo
    LaunchedEffect(currentMode, allProducts) {
        if (currentMode == AppMode.SCAN && allProducts.isNotEmpty()) {
            while (true) {
                delay(600)
                try {
                    val bitmap = previewViewRef?.bitmap
                    if (bitmap != null) {
                        viewModel.processScanFrame(bitmap) { match ->
                            feedbackController.playSupermarketBeep()
                        }
                    }
                } catch (e: Exception) {
                    // Safe guard
                }
            }
        }
    }

    // Color del escáner: Azul en Escaneo, Rojo en Registro
    val targetScannerColor = if (currentMode == AppMode.SCAN) {
        Color(0xFF00E5FF) // Azul cian neón
    } else {
        Color(0xFFFF1744) // Rojo vivo para modo registro
    }

    val animatedScannerColor by animateColorAsState(
        targetValue = targetScannerColor,
        animationSpec = tween(durationMillis = 300),
        label = "scanner_color"
    )

    // Diálogo con catálogo de productos
    if (showProductCatalogDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showProductCatalogDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Catálogo de Productos (${allProducts.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E2124)
                        )
                        IconButton(onClick = { showProductCatalogDialog = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (allProducts.isEmpty()) {
                        Text(
                            text = "No hay productos registrados aún. Toca el ícono de la cámara para registrar uno con 20 fotos.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(280.dp)) {
                            items(allProducts, key = { it.id }) { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF5F7FA))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF1E2124)
                                        )
                                        Text(
                                            text = "${CurrencyFormatter.formatCop(prod.price)} • ${prod.sampleCount} huellas",
                                            color = Color(0xFF00897B),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            viewModel.addManualTestScan(prod) {
                                                feedbackController.playSupermarketBeep()
                                            }
                                            showProductCatalogDialog = false
                                        }) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Simular paso por caja",
                                                tint = Color(0xFF00897B)
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deleteProduct(prod.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = Color(0xFFE53935)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIVISIÓN PRINCIPAL: 70% Cámara / 30% Blanco Puro
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("home_screen_container")
    ) {
        // TOP 70%: Zona de Cámara
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.70f)
                .background(Color(0xFF1E2124))
                .testTag("camera_viewport")
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        previewViewRef = previewView
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val cameraSelector = CameraSelector.Builder()
                                    .requireLensFacing(cameraSelectorFacing)
                                    .build()

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    update = { previewView ->
                        previewViewRef = previewView
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF26292E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Activar cámara",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Destello visual de obturador al tomar cada foto
            if (mappingProgress.justTookPhoto) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.40f))
                )
            }

            // Retícula y Láser animado (Azul o Rojo según el modo)
            ScannerOverlay(
                modifier = Modifier.fillMaxSize(),
                laserColor = animatedScannerColor,
                bracketColor = animatedScannerColor
            )

            // CONTADOR TRANSPARENTE PEQUEÑO 1, 2, 3 AL INICIAR MODO REGISTRO
            if (currentMode == AppMode.REGISTER && mappingProgress.isPreparingCountdown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${mappingProgress.countdownNumber}",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Barra superior: Bandera de Colombia + Catálogo + Ícono de Cámara para alternar de Modo
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusBarPadding.calculateTopPadding() + 8.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColombiaFlag(modifier = Modifier.testTag("colombia_flag_badge"))

                    if (allProducts.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${allProducts.size} prods",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { showProductCatalogDialog = true }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showProductCatalogDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = "Ver catálogo",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // LA FORMA EXCLUSIVA DE CAMBIAR DE MODO: ÍCONO DE CÁMARA
                    IconButton(
                        onClick = {
                            feedbackController.triggerModeSwitchHaptic()
                            if (currentMode == AppMode.SCAN) {
                                currentMode = AppMode.REGISTER
                                // Inicia automáticamente la preparación de 3 segundos y el rastreo de 20 imágenes
                                viewModel.startAutoRegistration(
                                    captureFrame = {
                                        try {
                                            previewViewRef?.bitmap
                                        } catch (e: Exception) {
                                            null
                                        }
                                    },
                                    onPhotoTakenSound = { feedbackController.playCameraShutterSound() },
                                    onSuccessSaved = {
                                        feedbackController.playSupermarketBeep()
                                        currentMode = AppMode.SCAN // Retorna automáticamente a modo normal
                                    }
                                )
                            } else {
                                // Cancela y regresa a modo normal
                                viewModel.cancelAutoRegistration()
                                currentMode = AppMode.SCAN
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("switch_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "Modo de cámara",
                            tint = if (currentMode == AppMode.SCAN) Color.White.copy(alpha = 0.9f) else Color(0xFFFF1744),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Notificación flotante sutil al detectar producto en Modo Escáner
            if (lastRecognition != null && currentMode == AppMode.SCAN) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE6004D40))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${lastRecognition?.product?.name}: ${CurrencyFormatter.formatCop(lastRecognition?.product?.price ?: 0)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // BOTTOM 30%: Zona Blanca Android con Contador de Dinero y Estado Limpio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f)
                .background(Color.White)
                .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 14.dp)
                .testTag("money_counter_section")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Contador "$ 0"
                    Text(
                        text = CurrencyFormatter.formatCop(totalMoney),
                        color = Color(0xFF00897B),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clickable { feedbackController.playSupermarketBeep() }
                            .testTag("money_counter_display")
                    )

                    // Estado de rastreo o botón de limpiar
                    if (currentMode == AppMode.REGISTER && mappingProgress.isRunning) {
                        // "Iniciado rastreo" limpiamente ubicado al lado del contador
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFBE9E7))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Iniciado rastreo (${mappingProgress.photosCaptured}/20)",
                                color = Color(0xFFD50000),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (totalMoney > 0L) {
                        TextButton(
                            onClick = {
                                viewModel.resetMoney()
                                feedbackController.triggerModeSwitchHaptic()
                            }
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Limpiar cuenta",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }

                // Subtexto mínimo y limpio
                if (currentMode == AppMode.REGISTER) {
                    if (mappingProgress.isPreparingCountdown) {
                        Text(
                            text = "Acomoda el producto frente a la cámara...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    } else if (mappingProgress.isRunning) {
                        Text(
                            text = "Moviendo el producto... se guardará automáticamente al completar 20",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = if (allProducts.isEmpty()) "Toca el ícono de la cámara para registrar un producto" else "${allProducts.size} producto(s) en catálogo",
                        color = Color.Gray.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
