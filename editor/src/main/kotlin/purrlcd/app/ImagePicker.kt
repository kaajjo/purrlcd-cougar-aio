package purrlcd.app

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun pickBackgroundImage(owner: Frame, title: String): String? {
    val dialog = FileDialog(owner, title, FileDialog.LOAD)
    dialog.file = "*.png;*.jpg;*.jpeg;*.bmp"
    dialog.isVisible = true
    val selected = dialog.file?.let { File(dialog.directory, it).absolutePath }
    dialog.dispose()
    return selected
}
