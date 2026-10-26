package com.tdtu.ckmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tdtu.ckmobile.ui.screens.TaskScreen
import com.tdtu.ckmobile.ui.theme.CKMobileTheme
import com.tdtu.ckmobile.ui.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CKMobileTheme {
                TaskScreen(viewModel = viewModel)
            }
        }
    }
}
