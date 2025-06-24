package com.github.zly2006.spatial

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.zly2006.spatial.ui.theme.SpatialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpatialTheme {
                var faceDetectEnabled by remember { mutableStateOf(true) }
                Scaffold { innerPadding ->
                    Row(Modifier.padding(innerPadding)) {
                        Button(onClick = {
                            val intent = Intent(this@MainActivity, AcgCharacterActivity::class.java)
                            intent.putExtra("faceDetectEnabled", faceDetectEnabled)
                            startActivity(intent)
                        }) {
                            Text("丛雨酱")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = {
                            startActivity(Intent(this@MainActivity, GyroscopeActivity::class.java))
                        }) {
                            Text("陀螺仪数据")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        androidx.compose.material3.Switch(
                            checked = faceDetectEnabled,
                            onCheckedChange = { faceDetectEnabled = it }
                        )

                        Text(if (faceDetectEnabled) "人脸识别开" else "人脸识别关", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
