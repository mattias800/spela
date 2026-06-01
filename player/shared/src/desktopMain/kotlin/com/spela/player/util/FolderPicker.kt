package com.spela.player.util

import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual fun pickDirectory(title: String): String? {
    var result: String? = null
    val task = Runnable {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            result = chooser.selectedFile?.absolutePath
        }
    }
    // The picker is modal and must run on the AWT event thread; callers are
    // on a background dispatcher, so marshal across.
    if (SwingUtilities.isEventDispatchThread()) {
        task.run()
    } else {
        SwingUtilities.invokeAndWait(task)
    }
    return result
}
