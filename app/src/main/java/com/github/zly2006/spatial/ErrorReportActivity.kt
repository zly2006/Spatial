package com.github.zly2006.spatial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.zly2006.spatial.ui.theme.SpatialTheme

class ErrorReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val errorMsg = intent.getStringExtra("error_message") ?: "Unknown error"
        val errorStack = intent.getStringExtra("error_stack") ?: "No stacktrace"
        setContent {
            SpatialTheme {
                ErrorReportScreen(errorMsg, errorStack)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorReportScreen(errorMsg: String, errorStack: String) {
    val clipboardManager = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("错误报告") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("错误信息：", style = MaterialTheme.typography.titleMedium)
            Text(errorMsg, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("堆栈信息：", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                clipboardManager.setText(AnnotatedString(errorStack))
            }) {
                Text("复制堆栈")
            }
            Box(modifier = Modifier.weight(1f)) {
                Text(errorStack, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
    }
}
