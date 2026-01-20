package com.debanshu.shaderlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.debanshu.shaderlab.imagelib.initImageExporter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initImageExporter(applicationContext)

        setContent {
            ShaderLabApp()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    ShaderLabApp()
}
