package com.github.zly2006.spatial

import android.Manifest
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.dropUnlessResumed
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val permissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )

    if (permissionState.status.isGranted) {
        // 如果权限已被授予，直接显示相机内容
        CameraContent()
    } else {
        // 如果权限被拒绝但可以请求，显示请求权限的内容
        CameraPermissionRequest(
            onRequestPermission = { permissionState.launchPermissionRequest() }
        )
    }
}

@Composable
fun CameraPermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "本应用需要相机权限来实现人脸检测功能。请点击下方按钮授予权限。",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRequestPermission) {
            Text("授予相机权限")
        }
    }
}

@Composable
private fun CameraContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // 保存检测到的人脸信息
    var detectedFaces by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }

    // 实现 DetectorListener
    val detectorListener = remember {
        object : FaceDetectorHelper.DetectorListener {
            override fun onResults(
                resultBundle: FaceDetectorHelper.ResultBundle
            ) {
                detectedFaces = resultBundle.results.firstOrNull()?.detections() ?: emptyList()
                imageWidth = resultBundle.inputImageWidth
                imageHeight = resultBundle.inputImageHeight
            }

            override fun onError(error: String, errorCode: Int) {
                Log.e("CameraScreen", "Face detection error: $error, code: $errorCode")
            }
        }
    }

    DisposableEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get(5, TimeUnit.SECONDS)
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        val faceDetectorHelper = FaceDetectorHelper(
            context = context,
            runningMode = RunningMode.LIVE_STREAM,
            faceDetectorListener = detectorListener
        )
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            faceDetectorHelper.detectLivestreamFrame(imageProxy)
        }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("CameraScreen", "Use case binding failed", exc)
        }
        onDispose {
            cameraProvider.unbindAll()
            executor.shutdown()
            faceDetectorHelper.clearFaceDetector()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 相机预览
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 绘制人脸检测结果
        if (detectedFaces.isNotEmpty() && imageWidth > 0 && imageHeight > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                detectedFaces.forEach { face ->
                    val box = face.boundingBox()

                    // 调整坐标到画布大小
                    val left = box.left / imageWidth.toFloat() * canvasWidth
                    val top = box.top / imageHeight.toFloat() * canvasHeight
                    val right = box.right / imageWidth.toFloat() * canvasWidth
                    val bottom = box.bottom / imageHeight.toFloat() * canvasHeight

                    // 绘制人脸框
                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 3f)
                    )

                    // 绘制关键点
                    face.keypoints().getOrNull()?.forEach { keypoint ->
                        val x = keypoint.x() / imageWidth.toFloat() * canvasWidth
                        val y = keypoint.y() / imageHeight.toFloat() * canvasHeight

                        drawCircle(
                            color = Color.Red,
                            center = Offset(x, y),
                            radius = 4f
                        )
                    }
                }
            }
        }
    }
}
