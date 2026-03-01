package sample.multiplatform

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import sample.multiplatform.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AspectK Multiplatform Sample",
    ) {
        App()
    }
}
