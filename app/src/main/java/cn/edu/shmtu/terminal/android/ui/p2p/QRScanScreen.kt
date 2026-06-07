package cn.edu.shmtu.terminal.android.ui.p2p

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cn.edu.shmtu.terminal.android.data.p2p.QRPayload
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX + ML Kit QR code scanning screen for P2P pairing.
 *
 * @param onQRScanned Callback invoked when a valid P2P QR payload is detected.
 * @param onBack Callback to navigate back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScanScreen(
    onQRScanned: (QRPayload) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var isScanning by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var lastRawValue by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要相机权限才能扫描二维码", Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    // Request camera permission if not granted
    if (!hasCameraPermission) {
        DisposableEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            onDispose { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描二维码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    isScanning = isScanning,
                    onBarcodeDetected = { barcode ->
                        if (!isScanning) return@CameraPreview
                        val rawValue = barcode.rawValue ?: return@CameraPreview

                        // Avoid processing the same QR code repeatedly
                        if (rawValue == lastRawValue) return@CameraPreview
                        lastRawValue = rawValue

                        Log.d("QRScanScreen", "=== QR code detected ===")
                        Log.d("QRScanScreen", "rawValue length: ${rawValue.length}")
                        Log.d("QRScanScreen", "rawValue preview: ${rawValue.take(200)}")

                        // Try to parse as QRPayload
                        try {
                            val payload = cn.edu.shmtu.terminal.android.data.p2p.p2pJson
                                .decodeFromString<QRPayload>(rawValue)
                            Log.d("QRScanScreen", "Parsed OK: ips=${payload.ips} port=${payload.port} pairCode='${payload.pairCode}' version=${payload.version}")

                            if (payload.ips.isEmpty()) {
                                Log.w("QRScanScreen", "Valid JSON but ips list is empty")
                                scanError = "二维码中未包含 IP 地址，无法配对"
                                isScanning = false
                                return@CameraPreview
                            }
                            if (payload.pairCode.isBlank()) {
                                Log.w("QRScanScreen", "Valid JSON but pairCode is blank")
                                scanError = "二维码中未包含配对码，无法配对"
                                isScanning = false
                                return@CameraPreview
                            }

                            // Valid P2P QR code — stop scanning and show confirm dialog
                            Log.i("QRScanScreen", "Valid P2P QR: ${payload.ips.size} IPs, pairCode=${payload.pairCode}")
                            isScanning = false
                            scanError = null
                            onQRScanned(payload)
                        } catch (e: Exception) {
                            // Not a valid P2P QR — show feedback so user knows SOMETHING was scanned
                            Log.w("QRScanScreen", "Failed to parse as P2P QR: ${e.javaClass.simpleName}: ${e.message}")
                            Log.d("QRScanScreen", "Raw content (first 100 chars): ${rawValue.take(100)}")
                            scanError = "这不是点对点配对二维码，请对准设备上的二维码重新扫描"
                            // Auto-clear after 2 seconds and keep scanning
                        }
                    }
                )

                // Show scan feedback overlay at the bottom
                if (scanError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = scanError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    scanError = null
                                    isScanning = true
                                    lastRawValue = null
                                }
                            ) {
                                Text("继续扫描")
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "需要相机权限才能扫描二维码",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * CameraX preview composable with ML Kit barcode analysis.
 */
@Composable
private fun CameraPreview(
    isScanning: Boolean,
    onBarcodeDetected: (Barcode) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create the camera provider future
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Create the barcode scanner once
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Image analysis for barcode scanning
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (!isScanning) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                processImageProxy(barcodeScanner, imageProxy, onBarcodeDetected)
                            }
                        }

                    // Select back camera
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    // Unbind previous use cases and bind
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("QRScanScreen", "Camera initialization failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Process a camera frame with ML Kit barcode scanner.
 */
private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: (Barcode) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                onBarcodeDetected(barcode)
            }
        }
        .addOnFailureListener { e ->
            Log.w("QRScanScreen", "Barcode scanning failed", e)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
