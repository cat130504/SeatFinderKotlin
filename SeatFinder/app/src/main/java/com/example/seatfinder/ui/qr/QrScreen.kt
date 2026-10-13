package com.example.seatfinder.ui.qr

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
fun QrScreen(
    onSuccessOccupazione: (Int, Double?, Double?) -> Unit = { _, _, _ -> },  // id tavolo, lat, lng
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isScanned by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasLocationPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(context, "Permesso camera negato", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
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
                                isScanned = isScanned,
                                onScanSuccess = { qrValue ->
                                    isScanned = true
                                    coroutineScope.launch {
                                        val id = qrValue.toIntOrNull() ?: return@launch
                                        val postiLiberi = TavoloRepository.getPostiLiberi(id)
                                        var rimasti = postiLiberi

                                        if (postiLiberi > 0) {
                                            var lat: Double? = null
                                            var lng: Double? = null
                                            if (hasLocationPermission) {
                                                try {
                                                    val location = fusedLocationClient.lastLocation.await()
                                                    lat = location?.latitude
                                                    lng = location?.longitude
                                                } catch (e: Exception) {
                                                    Log.w("QR", "Impossibile ottenere la posizione: ${e.message}")
                                                }
                                            }
                                            try {
                                                TavoloRepository.occupaTavolo(id, SessionManager.userId, lat, lng)
                                                rimasti = postiLiberi - 1
                                                val msg = when (postiLiberi) {
                                                    1 -> "Tavolo occupato, tutti i posti sono occupati"
                                                    2 -> "Tavolo occupato, $rimasti posto rimanente"
                                                    else -> "Tavolo occupato, $rimasti posti rimanenti"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                onSuccessOccupazione(id, lat, lng)
                                            } catch (e: Exception) {
                                                Log.e("QR", "Errore salvataggio tavolo", e)
                                                Toast.makeText(context, "Errore durante l'occupazione del tavolo", Toast.LENGTH_SHORT).show()
                                                isScanned = false
                                            }
                                        } else {
                                            Toast.makeText(context, "Tavolo pieno, non occupabile", Toast.LENGTH_SHORT).show()
                                            isScanned = false
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

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchOn) "Spegni torcia" else "Accendi torcia",
                        tint = Color.White
                    )
                }
            }

            // 👇 NUOVO: testo sotto il riquadro
            Text(
                text = "Inquadra il QR code del tavolo",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 340.dp)
            )

        } else {
            Text(
                text = "Serve il permesso per accedere alla fotocamera",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    isScanned: Boolean,
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
            if (!isScanned) {
                barcodes.firstOrNull()?.rawValue?.let { value ->
                    onScanSuccess(value)
                }
            }
        }
        .addOnFailureListener {
            Log.e("QR", "Errore scan", it)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
