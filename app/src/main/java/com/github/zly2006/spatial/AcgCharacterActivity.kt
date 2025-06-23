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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class FaceGyroscopeBlender() {
    private var refreshInterval = 100L // 默认刷新间隔为100毫秒
    private val horizontalFaceMove = mutableListOf<Float>()
}

class AcgCharacterActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val previewView = PreviewView(this)
        previewView.scaleX = 0.15f
        previewView.scaleY = 0.15f
        previewView.setOnClickListener {
            if (it.alpha == 1f) {
                it.alpha = 0f
            } else {
                it.alpha = 1f
            }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val executor = Executors.newSingleThreadExecutor()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var lastFaceDetectTime = System.currentTimeMillis()
        setContent {
            val permissionState = rememberPermissionState(
                permission = Manifest.permission.CAMERA
            )

            var faceDetectorResult by remember { mutableStateOf<FaceDetectorResult?>(null) }
            var imageWidth by remember { mutableIntStateOf(0) }
            var imageHeight by remember { mutableIntStateOf(0) }
            val detectorListener = remember {
                object : FaceDetectorHelper.DetectorListener {
                    override fun onResults(
                        resultBundle: FaceDetectorHelper.ResultBundle
                    ) {
                        faceDetectorResult = resultBundle.results.firstOrNull()
                        imageWidth = resultBundle.inputImageWidth
                        imageHeight = resultBundle.inputImageHeight
                        lastFaceDetectTime = System.currentTimeMillis() / 4 + lastFaceDetectTime * 3 / 4 // 平滑
                    }

                    override fun onError(error: String, errorCode: Int) {
                        Log.e("CameraScreen", "Face detection error: $error, code: $errorCode")
                    }
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
                val sliderState = remember { SliderState(0.7f) }
                var refRight by remember { mutableStateOf(FloatArray(3)) }
                var refUp by remember { mutableStateOf(FloatArray(3)) }
                var hasReference by remember { mutableStateOf(false) }
                var orientationAnglesState by remember { mutableStateOf(FloatArray(3)) }
                var rotationVectorDataState by remember { mutableStateOf(FloatArray(6)) }
                // 监听陀螺仪数据
                DisposableEffect(Unit) {
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                                val values = event.values
                                val rotationMatrix = FloatArray(9)
                                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                                val orientation = FloatArray(3)
                                SensorManager.getOrientation(rotationMatrix, orientation)
                                orientationAnglesState = orientation
                                rotationVectorDataState = values.plus(0f)
                            }
                        }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }
                    sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
                    onDispose {
                        sensorManager.unregisterListener(listener)
                    }
                }
                // 人脸检测用于自动重置陀螺仪基准点和调节敏感度
                LaunchedEffect(faceDetectorResult, imageWidth, imageHeight) {
                    if (faceDetectorResult != null && faceDetectorResult!!.detections()
                            .isNotEmpty() && imageWidth > 0 && imageHeight > 0
                    ) {
                        // 检测到人脸时自动重置陀螺仪基准点
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorDataState)

                        val faceDetectInterval = System.currentTimeMillis() - lastFaceDetectTime
                        if (hasReference) {
                            // 如果已经有参考点，则使用插值平滑过渡
                            refRight = floatArrayOf(
                                lerp(refRight[0], rotationMatrix[0], faceDetectInterval / 500f),
                                lerp(refRight[1], rotationMatrix[3], faceDetectInterval / 500f),
                                lerp(refRight[2], rotationMatrix[6], faceDetectInterval / 500f)
                            ).normalized3()
                            refUp = floatArrayOf(
                                lerp(refUp[0], rotationMatrix[1], faceDetectInterval / 500f),
                                lerp(refUp[1], rotationMatrix[4], faceDetectInterval / 500f),
                                lerp(refUp[2], rotationMatrix[7], faceDetectInterval / 500f)
                            ).normalized3()
                        }
                        else {
                            refRight = floatArrayOf(
                                rotationMatrix[0],
                                rotationMatrix[3],
                                rotationMatrix[6]
                            ).normalized3()
                            refUp = floatArrayOf(
                                rotationMatrix[1],
                                rotationMatrix[4],
                                rotationMatrix[7]
                            ).normalized3()
                        }

                        hasReference = true
                        // 根据人脸框大小动态调节敏感度（示例：人脸越大，敏感度越低，越小越高）
                        val face = faceDetectorResult!!.detections().first()
                        val box = face.boundingBox()
                        val faceSize =
                            sqrt(((box.right - box.left) * (box.bottom - box.top)) / (imageWidth * imageHeight))
                        sliderState.value = lerp(sliderState.value, faceSize * 2f, faceDetectInterval / 500f).coerceIn(0f, 1f)
                    }
                }
                val density = LocalDensity.current
                val offset by remember {
                    derivedStateOf {
                        if (hasReference) {
                            val rotationMatrix = FloatArray(9)
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorDataState)
                            val currentScreenNormal = floatArrayOf(
                                rotationMatrix[2], rotationMatrix[5], rotationMatrix[8]
                            )
                            val right = currentScreenNormal.dotProduct(refRight)
                            val up = currentScreenNormal.dotProduct(refUp)
                            val maxScale = density.run { 100.dp.toPx() }
                            val sensitivity = sliderState.value.coerceIn(0f, 1f)

                            IntOffset(
                                (right * sensitivity * maxScale).toInt(),
                                (up * sensitivity * maxScale).toInt()
                            )
                        }
                        else {
                            IntOffset(0, 0)
                        }
                    }
                }
                Greeting2(
                    offset = offset,
                    footer = {
                        Row(horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                if (faceDetectorResult == null) {
                                    Text("没有检测到人脸", textAlign = TextAlign.Center)
                                } else {
                                    val detections = faceDetectorResult!!.detections()
                                    Text(
                                        buildString {
                                            append("检测到 ")
                                            append(detections.size)
                                            append(" 个人脸")

                                            val box = faceDetectorResult?.detections()?.singleOrNull()?.boundingBox()
                                            if (box != null) {
                                                val faceSize =
                                                    sqrt(((box.right - box.left) * (box.bottom - box.top)) / (imageWidth * imageHeight))
                                                append("，人脸大小：${"%.3f".format(faceSize)}")
                                            }
                                        }
                                    )

                                }
                                Text("敏感度：" + "%.3f".format(sliderState.value))

                                val rotationMatrix = FloatArray(9)
                                SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorDataState)
                                val currentScreenNormal = floatArrayOf(
                                    rotationMatrix[2], rotationMatrix[5], rotationMatrix[8]
                                )
                                val right = currentScreenNormal.dotProduct(refRight)
                                val up = currentScreenNormal.dotProduct(refUp)

                                Text("当前屏幕法线：(${"%.3f".format(currentScreenNormal[0])}, ${"%.3f".format(currentScreenNormal[1])}, ${"%.3f".format(currentScreenNormal[2])})")
                                Text("右分量：${"%.3f".format(right)}， 上分量：${"%.3f".format(up)}")

                            }
                            Box(modifier = Modifier.background(Color.LightGray).wrapContentSize()) {
                                AndroidView(
                                    { previewView }, modifier = Modifier.height(80.dp)
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

fun lerp(a: Float, b: Float, t: Float): Float {
    return a + (b - a) * t
}

fun FloatArray.normalized3(): FloatArray {
    val norm = sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2])
    return if (norm > 0) {
        floatArrayOf(this[0] / norm, this[1] / norm, this[2] / norm)
    } else {
        floatArrayOf(0f, 0f, 0f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting2(modifier: Modifier = Modifier, sliderState: SliderState, footer: @Composable ColumnScope.() -> Unit = {}, offset: IntOffset = IntOffset(0, 0)) {
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
        val offset = with(density) {
            // Convert custom offset to IntOffset based on screen height
            IntOffset(
                offset.x * bgBitmap.height / configuration.screenHeightDp.dp.roundToPx(),
                offset.y * bgBitmap.height / configuration.screenHeightDp.dp.roundToPx(),
            )
        }
        Image(
            painter = BitmapPainter(
                bgBitmap,
                srcOffset = IntOffset(
                    ((bgBitmap.width - size.width) / 2 - offset.x).coerceIn(0, bgBitmap.width - size.width - 1),
                    ((0.05 * bgBitmap.height).toInt() - offset.y).coerceIn(0, bgBitmap.height - size.height - 1),
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
