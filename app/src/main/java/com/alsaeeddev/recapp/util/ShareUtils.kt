package com.alsaeeddev.recapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import java.io.File

object ShareUtils {

    fun shareRecordItem(context: Context, item: RecordItem) {
        try {
            val file = if (item.filePath.isNotEmpty()) File(item.filePath) else null
            val shareUri: Uri? = if (file != null && file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else if (item.uriString.isNotEmpty() && !item.uriString.startsWith("file://")) {
                Uri.parse(item.uriString)
            } else {
                null
            }

            if (shareUri == null) {
                Toast.makeText(context, "File does not exist for sharing", Toast.LENGTH_SHORT).show()
                return
            }

            val mimeType = if (item.mediaType == MediaType.VIDEO) "video/*" else "image/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ${item.title}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
