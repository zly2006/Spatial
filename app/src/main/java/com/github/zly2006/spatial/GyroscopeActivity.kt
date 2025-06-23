package com.github.zly2006.spatial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.spatial.ui.theme.SpatialTheme
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt

class GyroscopeActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager

    // 陀螺仪传感器
    private var gyroscopeSensor: Sensor? = null

    // 加速度传感器
    private var accelerometerSensor: Sensor? = null

    // 旋转向量传感器
    private var rotationVectorSensor: Sensor? = null

    // 传感器数据存储
    private var gyroscopeData = FloatArray(3)
    private var accelerometerData = FloatArray(3)
    private var rotationVectorData = FloatArray(6)
    private var orientationAngles = FloatArray(3)

    private var state by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化传感器管理器
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 获取传感器
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // 注册传感器监听器
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        setContent {
            SpatialTheme {
                GyroscopeDataScreen(
                    gyroscopeData = gyroscopeData,
                    accelerometerData = accelerometerData,
                    rotationVectorData = rotationVectorData,
                    orientationAngles = orientationAngles,
                    state = state,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 注册传感器监听器
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()

        // 取消注册传感器监听器
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        state++
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                // 陀螺仪数据 (rad/s)
                gyroscopeData = event.values.clone()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // 加速度数据 (m/s²)
                accelerometerData = event.values.clone()
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                // 旋转向量数据
                rotationVectorData = event.values.plus(0f)
                rotationVectorData[5] = acos(event.values[3]) / Math.PI.toFloat() * 360 // 计算旋转角（角度）

                // 计算方向角
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 处理传感器精度变化（如有需要）
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GyroscopeDataScreen(
    gyroscopeData: FloatArray,
    accelerometerData: FloatArray,
    rotationVectorData: FloatArray,
    orientationAngles: FloatArray,
    state: Int
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("陀螺仪数据监测") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (state == 0) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 陀螺仪数据显示区域
            SensorDataCard(
                title = "陀螺仪数据 (rad/s)",
                data = gyroscopeData,
                labels = arrayOf("X轴旋转速率", "Y轴旋转速率", "Z轴旋转速率"),
                colors = arrayOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFF44336))
            )

            // 加速度数据显示区域
            SensorDataCard(
                title = "加速度数据 (m/s²)",
                data = accelerometerData,
                labels = arrayOf("X轴加速度", "Y轴加速度", "Z轴加速度"),
                colors = arrayOf(Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF607D8B))
            )

            // 方向角数据显示区域
            SensorDataCard(
                title = "设备方向角 (弧度)",
                data = orientationAngles,
                labels = arrayOf("方位角 (Azimuth)", "俯仰角 (Pitch)", "滚转角 (Roll)"),
                colors = arrayOf(Color(0xFFE91E63), Color(0xFF3F51B5), Color(0xFF009688)),
                range = -Math.PI.toFloat()..Math.PI.toFloat()
            )

            // 旋转向量数据显示区域
            SensorDataCard(
                title = "旋转向量数据",
                data = rotationVectorData,
                labels = arrayOf("X分量", "Y分量", "Z分量", "标量分量", "精度估计", "旋转角（角度）"),
                colors = arrayOf(
                    Color(0xFFFF5722), Color(0xFF8BC34A), Color(0xFF673AB7),
                    Color(0xFF795548), Color(0xFF00BCD4), Color(0xFFCDDC39),
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "参考点设定",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),)

                    var screenRight by remember { mutableStateOf(FloatArray(3)) }
                    var screenUp by remember { mutableStateOf(FloatArray(3)) }
                    Button(
                        onClick = {
                            val rotationMatrix = FloatArray(9)
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorData)
                            screenRight = floatArrayOf(
                                rotationMatrix[0],
                                rotationMatrix[3],
                                rotationMatrix[6]
                            )
                            screenUp = floatArrayOf(
                                rotationMatrix[1],
                                rotationMatrix[4],
                                rotationMatrix[7]
                            )
                        }
                    ) {
                        Text("设置参考点")
                    }

                    if (screenRight.sum() != 0f && screenUp.sum() != 0f) {
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorData)
                        val currentScreenNormal = floatArrayOf(
                            rotationMatrix[2],
                            rotationMatrix[5],
                            rotationMatrix[8]
                        )
                        val right = currentScreenNormal.dotProduct(screenRight)
                        val up = currentScreenNormal.dotProduct(screenUp)
                        Text(
                            text = "当前设备方向：\n" +
                                    when {
                                        right > 0 && abs(right) > abs(up) -> "右侧"
                                        right < 0 && abs(right) > abs(up) -> "左侧"
                                        up > 0 && abs(up) > abs(right) -> "上侧"
                                        up < 0 && abs(up) > abs(right) -> "下侧"
                                        else -> "未知方向"
                                    } + "\n" +
                                    "右侧分量: ${"%.4f".format(right)}\n" +
                                    "上侧分量: ${"%.4f".format(up)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "请先设置参考点",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 设备倾斜可视化
            DeviceTiltVisualization(
                pitch = orientationAngles[1],
                roll = orientationAngles[2]
            )
        }
    }
}

fun FloatArray.dotProduct(other: FloatArray): Float {
    require(this.size == other.size) { "Both arrays must have the same size." }
    return this.indices.map { this[it] * other[it] }.sum()
}

@Composable
fun SensorDataCard(
    title: String,
    data: FloatArray,
    labels: Array<String>,
    colors: Array<Color>,
    range: ClosedFloatingPointRange<Float> = -10f..10f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            for (i in data.indices.take(minOf(data.size, labels.size, colors.size))) {
                val value = (data[i] * 1000).roundToInt() / 1000f // 保留三位小数

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = labels[i],
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = value.toString(),
                        color = colors[i],
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { (value.coerceIn(range.start, range.endInclusive) - range.start) / (range.endInclusive - range.start) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = colors[i],
                    trackColor = colors[i].copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun DeviceTiltVisualization(pitch: Float, roll: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "设备倾斜可视化",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                // 通过旋转方向角来显示一个矩形，表示设备倾斜
                // 注意：这只是一个简单的可视化示例
                Box(
                    modifier = Modifier
                        .size(120.dp, 70.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "设备",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 显示当前的方向角数值
                Text(
                    text = "Pitch: ${(pitch * 57.3).roundToInt()}°\nRoll: ${(roll * 57.3).roundToInt()}°",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
