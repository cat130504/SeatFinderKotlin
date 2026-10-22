package com.example.seatfinder.ui.qr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.seatfinder.auth.SessionManager
import com.example.seatfinder.data.repository.TavoloRepository
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Timeout minimo tra una scansione e l'altra (3 secondi)
private const val SCAN_COOLDOWN_MS = 3000L

private fun checkCamera(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

private fun checkFineLocation(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun checkBackgroundLocation(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun apriImpostazioniApp(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@SuppressLint("MissingPermission")
@Composable
fun QrScreen(
    onSuccessOccupazione: (Int, Double?, Double?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Lock atomico immediato (non aspetta la ricomposizione di Compose)
    val isProcessingScan = remember { AtomicBoolean(false) }
    // Timestamp dell'ultima scansione per il timeout/cooldown
    val lastScanTimestamp = remember { AtomicLong(0L) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    var hasCameraPermission by remember { mutableStateOf(checkCamera(context)) }
    var hasFineLocation by remember { mutableStateOf(checkFineLocation(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(checkBackgroundLocation(context)) }

    var clickManualeUtente by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = checkCamera(context)
                hasFineLocation = checkFineLocation(context)
                hasBackgroundLocation = checkBackgroundLocation(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasBackgroundLocation = granted
            if (!granted && clickManualeUtente) {
                apriImpostazioniApp(context)
            }
            clickManualeUtente = false
        }
    )

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { risultati ->
            val granted = risultati[Manifest.permission.ACCESS_FINE_LOCATION] == true
            hasFineLocation = granted

            if (granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundLocation(context)) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else if (clickManualeUtente) {
                val activity = context.findActivity()
                val puoMostrarePopup = activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it, Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } ?: false

                if (!puoMostrarePopup) {
                    Toast.makeText(
                        context,
                        "Abilita la posizione precisa (\"Consenti sempre\") dalle Impostazioni",
                        Toast.LENGTH_LONG
                    ).show()
                    apriImpostazioniApp(context)
                }
            }
            clickManualeUtente = false
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                if (!hasFineLocation) {
                    foregroundLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            } else if (clickManualeUtente) {
                val activity = context.findActivity()
                val puoMostrarePopup = activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
                } ?: false

                if (!puoMostrarePopup) {
                    Toast.makeText(
                        context,
                        "Abilita il permesso Fotocamera dalle Impostazioni",
                        Toast.LENGTH_LONG
                    ).show()
                    apriImpostazioniApp(context)
                }
            }
            clickManualeUtente = false
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission && hasFineLocation && hasBackgroundLocation) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            processImage(
                                imageProxy = imageProxy,
                                scanner = scanner,
                                onScanSuccess = { qrValue ->
                                    val now = SystemClock.elapsedRealtime()

                                    // 1. Controlla il timeout (se sono passati meno di 3 secondi dall'ultimo scan, ignora)
                                    if (now - lastScanTimestamp.get() < SCAN_COOLDOWN_MS) {
                                        return@processImage
                                    }

                                    // 2. Lock atomico: se compareAndSet restituisce false, un'altra scansione è già in corso
                                    if (!isProcessingScan.compareAndSet(false, true)) {
                                        return@processImage
                                    }

                                    // Aggiorna subito il timestamp dell'ultimo tentativo valido
                                    lastScanTimestamp.set(now)

                                    if (!checkFineLocation(ctx) || !checkBackgroundLocation(ctx)) {
                                        Toast.makeText(
                                            ctx,
                                            "Permesso posizione 'Consenti sempre' mancante!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        isProcessingScan.set(false)
                                        return@processImage
                                    }

                                    val id = qrValue.toIntOrNull()
                                    if (id == null) {
                                        isProcessingScan.set(false)
                                        return@processImage
                                    }

                                    coroutineScope.launch {
                                        try {
                                            val postiLiberi = TavoloRepository.getPostiLiberi(id)

                                            if (postiLiberi > 0) {
                                                var lat: Double? = null
                                                var lng: Double? = null
                                                try {
                                                    val location = fusedLocationClient.lastLocation.await()
                                                    lat = location?.latitude
                                                    lng = location?.longitude
                                                } catch (e: Exception) {
                                                    Log.w("QR", "Impossibile ottenere la posizione: ${e.message}")
                                                }

                                                TavoloRepository.occupaTavolo(id, SessionManager.userId, lat, lng)

                                                // Ferma completamente l'analyzer dopo il successo così non legge più nulla
                                                analysis.clearAnalyzer()

                                                val rimasti = postiLiberi - 1
                                                val msg = when (postiLiberi) {
                                                    1 -> "Tavolo occupato, tutti i posti sono occupati"
                                                    2 -> "Tavolo occupato, $rimasti posto rimanente"
                                                    else -> "Tavolo occupato, $rimasti posti rimanenti"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                onSuccessOccupazione(id, lat, lng)
                                                // Nota: in caso di successo NON rimettiamo isProcessingScan a false,
                                                // così rimane bloccato finché la schermata non si chiude/cambia.
                                            } else {
                                                Toast.makeText(context, "Tavolo pieno, non occupabile", Toast.LENGTH_SHORT).show()
                                                // Sblocca per permettere di inquadrare un altro tavolo (dopo il cooldown di 3s)
                                                isProcessingScan.set(false)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("QR", "Errore salvataggio tavolo", e)
                                            Toast.makeText(context, "Errore durante l'occupazione del tavolo", Toast.LENGTH_SHORT).show()
                                            isProcessingScan.set(false)
                                        }
                                    }
                                }
                            )
                        }

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                analysis
                            )
                            torchOn = false
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                        } catch (e: Exception) {
                            Log.e("QR", "Errore fotocamera", e)
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            ScannerOverlay()

            Text(
                text = "Inquadra il QR code del tavolo",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 180.dp)
            )

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchOn) "Spegni torcia" else "Accendi torcia",
                        tint = Color.White
                    )
                }
            }

        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val messaggio = when {
                    !hasCameraPermission ->
                        "Serve il permesso per accedere alla fotocamera per poter leggere il QR code del tavolo."
                    !hasFineLocation ->
                        "Per occupare un tavolo è necessario concedere il permesso di posizione precisa e impostarlo su \"Consenti sempre\"."
                    else ->
                        "Hai concesso la posizione solo mentre l'app è in uso.\n\n" +
                                "Per occupare un tavolo devi impostare il permesso di posizione su \"Consenti sempre\", " +
                                "così l'app può liberare automaticamente il posto quando ti allontani."
                }

                Text(
                    text = messaggio,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        clickManualeUtente = true
                        when {
                            !hasCameraPermission -> {
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            }
                            !hasFineLocation -> {
                                foregroundLocationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            else -> {
                                apriImpostazioniApp(context)
                            }
                        }
                    }
                ) {
                    val testoBottone = when {
                        !hasCameraPermission -> "Concedi Fotocamera"
                        !hasFineLocation -> "Concedi Posizione"
                        else -> "Apri Impostazioni (\"Consenti sempre\")"
                    }
                    Text(text = testoBottone)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onScanSuccess: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees
    )

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let { value ->
                onScanSuccess(value)
            }
        }
        .addOnFailureListener {
            Log.e("QR", "Errore scan", it)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}