package purrlcd

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import org.jetbrains.compose.resources.stringResource
import purrlcd.app.parseLaunchOptions
import purrlcd.app.pickBackgroundImage
import purrlcd.engine.EngineClient
import purrlcd.resources.Res
import purrlcd.resources.app_title
import purrlcd.resources.choose_image_title
import purrlcd.ui.EditorScreen
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.PurrLCDTheme

fun main(args: Array<String>) {
    val config = parseLaunchOptions(args)
    val client = EngineClient(config.engine, config.data)
    application {
        val state = rememberWindowState(width = EditorDimensions.WindowWidth, height = EditorDimensions.WindowHeight)
        Window(
            onCloseRequest = { client.close(); exitApplication() },
            title = stringResource(Res.string.app_title),
            state = state
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(1120, 780) }
            PurrLCDTheme {
                val imagePickerTitle = stringResource(Res.string.choose_image_title)
                EditorScreen(client, config.startEngine, pickImage = { pickBackgroundImage(window, imagePickerTitle) })
            }
        }
    }
}
