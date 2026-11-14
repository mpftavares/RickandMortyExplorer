package com.example.rickandmortyexplorer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun CameraScreen(onPhotoTaken: (File, Location?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(isGranted(context, Manifest.permission.CAMERA)) }
    var hasLocation by remember { mutableStateOf(isLocationGranted(context)) }
    // pede câmara e localização na mesma janela; a localização é opcional (a foto fica sem coordenadas)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = it[Manifest.permission.CAMERA] == true
        hasLocation = isLocationGranted(context)
    }
    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        if (!hasCamera || !hasLocation) permissionLauncher.launch(permissions)
    }

    if (hasCamera) {
        CameraPreview(hasLocation, onPhotoTaken)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is needed to take photos")
            Button(onClick = { permissionLauncher.launch(permissions) }) { Text("Grant permission") }
        }
    }
}

@Composable
private fun CameraPreview(hasLocation: Boolean, onPhotoTaken: (File, Location?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var error by remember { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf<Location?>(null) }
    // pede a posição logo ao abrir, para já estar disponível quando se tira a foto
    LaunchedEffect(hasLocation) {
        if (hasLocation) requestLocation(context) { location = it }
    }
    // o controller junta preview + captura e liga/desliga a câmara com o ciclo de vida do ecrã
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    // liga a câmara ao abrir o ecrã e desliga-a ao sair (senão fica aberta enquanto a Activity estiver ativa)
    DisposableEffect(controller) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(location?.let { "📍 %.5f, %.5f".format(it.latitude, it.longitude) } ?: "📍 No location yet")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val file = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
                controller.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) = onPhotoTaken(file, location)
                        override fun onError(exception: ImageCaptureException) {
                            error = "Capture failed: ${exception.message}"
                        }
                    }
                )
            }) { Text("📸 Capture") }
        }
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isLocationGranted(context: Context) =
    isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

// pede uma posição atual ao GPS (ou à rede, se o GPS estiver desligado); devolve null se não conseguir
@SuppressLint("MissingPermission") // só é chamada depois de isLocationGranted()
private fun requestLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { manager.isProviderEnabled(it) }
        ?: return onResult(null)
    val executor = ContextCompat.getMainExecutor(context)
    LocationManagerCompat.getCurrentLocation(manager, provider, CancellationSignal(), executor) { onResult(it) }
}
