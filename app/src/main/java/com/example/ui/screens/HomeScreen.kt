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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    SCAN,     // Modo normal de escaneo / cobro (Azul)
    REGISTER  // Modo registrar producto / fotos guiadas (Rojo)
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

    val cameraSelectorFacing by remember {
        mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
    }

    var currentMode by remember {
        mutableStateOf(AppMode.SCAN)
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showProductCatalogDialog by remember { mutableStateOf(false) }

    val totalMoney by viewModel.totalMoney.collectAsStateWithLifecycle()
    val mappingProgress by viewModel.mappingProgress.collectAsStateWithLifecycle()
    val isWaitingForDetails by viewModel.isWaitingForDetails.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val lastRecognition by viewModel.lastRecognition.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val inputName by viewModel.inputProductName.collectAsStateWithLifecycle()
    val inputPrice by viewModel.inputProductPrice.collectAsStateWithLifecycle()

    // Bucle continuo de escaneo en tiempo real en Modo Escaneo
    LaunchedEffect(currentMode, allProducts, isWaitingForDetails) {
        if (currentMode == AppMode.SCAN && allProducts.isNotEmpty() && !isWaitingForDetails) {
            while (true) {
                delay(550)
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

    // Color del escáner: Azul cian neón en Escaneo, Rojo vivo en Registro
    val targetScannerColor = if (currentMode == AppMode.SCAN) {
        Color(0xFF00E5FF)
    } else {
        Color(0xFFFF1744)
    }

    val animatedScannerColor by animateColorAsState(
        targetValue = targetScannerColor,
        animationSpec = tween(durationMillis = 300),
        label = "scanner_color"
    )

    // DIÁLOGO CON CATÁLOGO DE PRODUCTOS EN BASE DE DATOS
    if (showProductCatalogDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showProductCatalogDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Mis Productos Registrados",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E2124)
                            )
                            Text(
                                text = "${allProducts.size} producto(s) en base de datos",
                                fontSize = 12.sp,
                                color = Color(0xFF00897B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = { showProductCatalogDialog = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (allProducts.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aún no tienes productos registrados.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Toca el ícono de la cámara arriba para registrar tu primer producto.",
                                color = Color.Gray.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(280.dp)) {
                            items(allProducts, key = { it.id }) { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF5F7FA))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E2124)
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCop(prod.price),
                                            color = Color(0xFF00897B),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(onClick = { viewModel.deleteProduct(prod.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(20.dp)
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

    // PANTALLA PRINCIPAL: 70% Cámara / 30% Panel Inferior
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("home_screen_container")
    ) {
        // TOP 70%: Zona de Cámara con Visor y Retícula
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
                        text = "Activar permiso de cámara",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // Destello visual al capturar fotos
            if (mappingProgress.justTookPhoto) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.40f))
                )
            }

            // Retícula y Láser animado
            ScannerOverlay(
                modifier = Modifier.fillMaxSize(),
                laserColor = animatedScannerColor,
                bracketColor = animatedScannerColor
            )

            // CONTADOR TRANSPARENTE 3, 2, 1 AL TOCAR LA CÁMARA
            if (currentMode == AppMode.REGISTER && mappingProgress.isPreparingCountdown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Acomoda el producto: ${mappingProgress.countdownNumber}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Barra superior: Bandera de Colombia + Catálogo + Ícono de Cámara
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

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${allProducts.size} productos",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { showProductCatalogDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showProductCatalogDialog = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = "Ver catálogo",
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // ÍCONO DE CÁMARA: INICIA EN 3 SEGUNDOS EL MAPEO COMO YA LO TENÍAS
                    IconButton(
                        onClick = {
                            feedbackController.triggerModeSwitchHaptic()
                            if (currentMode == AppMode.SCAN) {
                                currentMode = AppMode.REGISTER
                                // Inicia inmediatamente la cuenta regresiva de 3 segundos y el mapeo
                                viewModel.startAutoRegistration(
                                    captureFrame = {
                                        try {
                                            previewViewRef?.bitmap
                                        } catch (e: Exception) {
                                            null
                                        }
                                    },
                                    onPhotoTakenSound = { feedbackController.playCameraShutterSound() },
                                    onMappingFinished = {
                                        feedbackController.playCameraShutterSound()
                                    }
                                )
                            } else {
                                // Si estaba en modo registro, cancela y regresa al escáner azul
                                viewModel.cancelRegistration()
                                currentMode = AppMode.SCAN
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (currentMode == AppMode.REGISTER) Color(0xFFFF1744) else Color.Black.copy(alpha = 0.45f))
                            .testTag("switch_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "Modo cámara",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Notificación de producto reconocido en Modo Escáner
            if (lastRecognition != null && currentMode == AppMode.SCAN) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE6004D40))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${lastRecognition!!.product.name}: ${CurrencyFormatter.formatCop(lastRecognition!!.product.price)}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Mensajes flotantes del sistema
            if (userMessage != null && lastRecognition == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xD9212121))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = userMessage!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // BOTTOM 30%: Zona Blanca (Dinero en Escaneo, o Recuadros de Nombre/Precio tras Mapeo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f)
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("money_counter_section")
        ) {
            if (isWaitingForDetails) {
                // CUANDO TERMINA EL MAPEO: SALEN ABAJITO DOS RECUADROS PEQUEÑITOS PARA PONER NOMBRE Y PRECIO, Y BOTÓN GUARDAR
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "15 fotos tomadas. Asigna nombre y precio:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E2124)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Recuadro 1: Nombre del producto
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { viewModel.onInputNameChange(it) },
                            placeholder = { Text("Nombre", fontSize = 13.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            modifier = Modifier.weight(0.58f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF1744),
                                unfocusedBorderColor = Color.LightGray
                            )
                        )

                        // Recuadro 2: Precio del producto
                        OutlinedTextField(
                            value = inputPrice,
                            onValueChange = { viewModel.onInputPriceChange(it) },
                            placeholder = { Text("Precio", fontSize = 13.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            prefix = { Text("$", fontSize = 13.sp, color = Color.Gray) },
                            modifier = Modifier.weight(0.42f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF1744),
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.cancelRegistration()
                                currentMode = AppMode.SCAN
                                feedbackController.triggerModeSwitchHaptic()
                            }
                        ) {
                            Text("Cancelar", color = Color.Gray, fontSize = 12.sp)
                        }

                        // Botón abajito que dice Guardar
                        Button(
                            onClick = {
                                viewModel.confirmSaveProduct(
                                    onSuccessSaved = {
                                        feedbackController.playSupermarketBeep()
                                        currentMode = AppMode.SCAN
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else if (currentMode == AppMode.REGISTER && mappingProgress.isRunning) {
                // Durante la captura de las 15 fotos
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Mapeando producto: ${mappingProgress.photosCaptured} / ${mappingProgress.maxPhotos} fotos",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD50000)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gira suavemente el producto frente al recuadro",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                // Modo Escaneo Normal (Azul) con Contador de Dinero
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        if (totalMoney > 0L) {
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

                    Text(
                        text = if (allProducts.isEmpty()) {
                            "Toca el ícono de la cámara arriba para registrar tu primer producto"
                        } else {
                            "Pasa un producto por el escáner para sumar al total"
                        },
                        color = Color.Gray.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
