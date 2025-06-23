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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.zly2006.spatial.ui.theme.SpatialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpatialTheme {
                Scaffold { innerPadding ->
                    Row(Modifier.padding(innerPadding)) {
                        Button(onClick = {
                            startActivity(Intent(this@MainActivity, AcgCharacterActivity::class.java))
                        }) {
                            Text("丛雨酱")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = {
                            startActivity(Intent(this@MainActivity, GyroscopeActivity::class.java))
                        }) {
                            Text("陀螺仪数据")
                        }
                    }
                }
            }
        }
    }
}
