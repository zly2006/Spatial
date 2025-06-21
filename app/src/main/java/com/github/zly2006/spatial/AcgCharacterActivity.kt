package com.github.zly2006.spatial

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.spatial.ui.theme.SpatialTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.*

class AcgCharacterActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val previewView = PreviewView(this)
        previewView.scaleX = 0.15f
        previewView.scaleY = 0.15f
        previewView
        previewView.setOnClickListener {
            if (it.alpha == 1f) {
                it.alpha = 0f
            } else {
                it.alpha = 1f
            }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val executor = Executors.newSingleThreadExecutor()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        setContent {
            val permissionState = rememberPermissionState(
                permission = Manifest.permission.CAMERA
            )

            var faceDetectorResult by remember { mutableStateOf<FaceDetectorResult?>(null) }
            var imageWidth by remember { mutableIntStateOf(0) }
            var imageHeight by remember { mutableIntStateOf(0) }
            var lastFaceDetectionTime by remember { mutableLongStateOf(0L) }
            var hasFace by remember { mutableStateOf(false) }

            // 陀螺仪相关状态
            var gyroOffsetX by remember { mutableFloatStateOf(0f) }
            var gyroOffsetY by remember { mutableFloatStateOf(0f) }
            var movementIntensity by remember { mutableFloatStateOf(0f) }

            // 使用动画插值来平滑过渡
            var currentOffsetX by remember { mutableFloatStateOf(0f) }
            var currentOffsetY by remember { mutableFloatStateOf(0f) }
            var targetOffsetX by remember { mutableFloatStateOf(0f) }
            var targetOffsetY by remember { mutableFloatStateOf(0f) }

            // 自动调整的敏感度
            var autoSensitivity by remember { mutableFloatStateOf(0.5f) }

            val detectorListener = remember {
                object : FaceDetectorHelper.DetectorListener {
                    override fun onResults(
                        resultBundle: FaceDetectorHelper.ResultBundle
                    ) {
                        faceDetectorResult = resultBundle.results.firstOrNull()
                        imageWidth = resultBundle.inputImageWidth
                        imageHeight = resultBundle.inputImageHeight

                        // 更新最后一次人脸检测时间和状态
                        if (faceDetectorResult?.detections()?.isNotEmpty() == true) {
                            lastFaceDetectionTime = System.currentTimeMillis()
                            hasFace = true
                        } else if (System.currentTimeMillis() - lastFaceDetectionTime > 1000) {
                            // 如果1秒内没有检测到人脸，则认为没有人脸
                            hasFace = false
                        }
                    }

                    override fun onError(error: String, errorCode: Int) {
                        Log.e("CameraScreen", "Face detection error: $error, code: $errorCode")
                    }
                }
            }
            val sensorListener = remember {
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                            // 处理陀螺仪数据
                            val rotationMatrix = FloatArray(9)
                            val orientationAngles = FloatArray(3)

                            // 将旋转向量转换为旋转矩阵
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                            // 从旋转矩阵计算方向角
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)

                            // 获取设备旋转角度（弧度）
                            val azimuth = orientationAngles[0] // 绕Z轴旋转
                            val pitch = orientationAngles[1]   // 绕X轴旋转
                            val roll = orientationAngles[2]    // 绕Y轴旋转

                            // 计算陀螺仪偏移量，使用适当的缩放因子
                            gyroOffsetX = roll * 3.0f  // 左右倾斜对应X轴偏移
                            gyroOffsetY = pitch * 3.0f // 前后倾斜对应Y轴偏移

                            // 计算设备移动强度，用于调整敏感度
                            val deltaRotation = sqrt(event.values.sumOf { it.toDouble().pow(2) }.toFloat())
                            movementIntensity = 0.9f * movementIntensity + 0.1f * deltaRotation

                            // 根据移动强度自动调整敏感度
                            // 移动越剧烈，敏感度越低，提供更稳定的体验
                            val targetSensitivity = (1.0f - (movementIntensity * 0.5f)).coerceIn(0.1f, 0.9f)
                            autoSensitivity = 0.95f * autoSensitivity + 0.05f * targetSensitivity
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                        // 无需处理
                    }
                }
            }
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)

            // 使用协程执行动画平滑过渡
            LaunchedEffect(Unit) {
                while (true) {
                    if (hasFace && faceDetectorResult?.detections()?.size == 1) {
                        // 使用人脸检测结果作为目标偏移
                        val detection = faceDetectorResult!!.detections()[0]
                        val box = detection.boundingBox()
                        val xOffset = box.centerX() / imageWidth - 0.5
                        val yOffset = box.centerY() / imageHeight - 0.5

                        targetOffsetX = xOffset.toFloat()
                        targetOffsetY = yOffset.toFloat()
                    } else {
                        // 使用陀螺仪数据作为目标偏移
                        targetOffsetX = gyroOffsetX
                        targetOffsetY = gyroOffsetY
                    }

                    // 平滑过渡（ease-in-out效果）
                    currentOffsetX += (targetOffsetX - currentOffsetX) * 0.1f
                    currentOffsetY += (targetOffsetY - currentOffsetY) * 0.1f

                    delay(16) // 约60fps的刷新率
                }
            }

            // 清理监听器
            DisposableEffect(Unit) {
                onDispose {
                    sensorManager.unregisterListener(sensorListener)
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(Unit) {
                val cameraProvider = cameraProviderFuture.get(5, TimeUnit.SECONDS)
                val preview = androidx.camera.core.Preview.Builder()
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
                    context = this@AcgCharacterActivity,
                    runningMode = RunningMode.LIVE_STREAM,
                    faceDetectorListener = detectorListener
                )
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    faceDetectorHelper.detectLivestreamFrame(imageProxy)
                }
                try {
                    faceDetectorHelper.setupFaceDetector()
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


            if (permissionState.status.isGranted) {
                // 如果权限已被授予，直接显示相机内容
            } else {
                // 如果权限被拒绝但可以请求，显示请求权限的内容
                CameraPermissionRequest(
                    onRequestPermission = { permissionState.launchPermissionRequest() }
                )
            }

            SpatialTheme {
                val sliderState = remember { SliderState(autoSensitivity) }

                // 使用陀螺仪调整的敏感度同步到滑块
                LaunchedEffect(autoSensitivity) {
                    sliderState.value = autoSensitivity
                }

                Greeting2(
                    offset = {
                        val maxScale = 100.dp.toPx()
                        val sensitivity = sliderState.value.coerceIn(0f, 1f)

                        IntOffset(
                            (atan(currentOffsetX) * sensitivity * maxScale).toInt(),
                            (atan(currentOffsetY) * sensitivity * maxScale).toInt()
                        )
                    },
                    footer = {
                        Row(horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                if (hasFace) {
                                    Text("已检测到人脸", textAlign = TextAlign.Center)
                                } else {
                                    Text("使用陀螺仪控制", textAlign = TextAlign.Center)
                                }
                                Text("敏感度：" + "%.3f".format(sliderState.value))
                            }
                            Box(modifier = Modifier.background(Color.LightGray).wrapContentSize()) {
                                AndroidView(
                                    {
                                        previewView
                                    }, modifier = Modifier.height(80.dp)
                                )
                            }
                        }
                    },
                    sliderState = sliderState
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting2(modifier: Modifier = Modifier, sliderState: SliderState, footer: @Composable ColumnScope.() -> Unit = {}, offset: Density.() -> IntOffset = { IntOffset(0, 0) }) {
    val configuration = LocalConfiguration.current // Access configuration to get screen dimensions
    var aspectRatio by remember { mutableDoubleStateOf(configuration.screenWidthDp.toDouble() / configuration.screenHeightDp) }
    Box(
        modifier = modifier.fillMaxSize().onGloballyPositioned {
            aspectRatio = it.size.width.toDouble() / it.size.height.toDouble()
        },
        contentAlignment = Alignment.Center, // Align foreground to center
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val bgBitmap = remember {
            ImageBitmap.imageResource(context.resources, R.drawable.yama_no_naka)
        }
        val size = IntSize((bgBitmap.height * 0.9 * aspectRatio).toInt(), (bgBitmap.height * 0.9).toInt())
        val customOffset = offset(density)
        val offset = with(density) {
            // Convert custom offset to IntOffset based on screen height
            IntOffset(
                customOffset.x * bgBitmap.height / configuration.screenHeightDp.dp.roundToPx(),
                customOffset.y * bgBitmap.height / configuration.screenHeightDp.dp.roundToPx(),
            )
        }
        Image(
            painter = BitmapPainter(
                bgBitmap,
                srcOffset = IntOffset(
                    (bgBitmap.width - size.width) / 2 + offset.x,
                    (0.05 * bgBitmap.height).toInt() + offset.y,
                ),
                srcSize = size
            ),
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Image(
            painter = painterResource(R.drawable.murasame),
            contentDescription = "Character Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillHeight,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val interactionSource = remember { MutableInteractionSource() }
                Slider(sliderState,
                    interactionSource = interactionSource,
                    thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        thumbSize = DpSize(20.dp, 20.dp),
                    )
                })
                Text("敏感度调节")
                footer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
    SpatialTheme {
        Greeting2(
            footer = {
                Text("Example Footer")
            },
            sliderState = remember { SliderState(0.4f
            ) }
        )
    }
}
