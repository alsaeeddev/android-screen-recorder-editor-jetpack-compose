package com.alsaeeddev.recapp.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.util.FormatUtils
import com.alsaeeddev.recapp.util.ShareUtils
import java.io.File

@Composable
fun ScreenshotPreviewDialog(
    item: RecordItem,
    onDismiss: () -> Unit,
    onFavoriteToggle: (RecordItem) -> Unit,
    onDelete: (RecordItem) -> Unit
) {
    val context = LocalContext.current

    var isFavorite by remember(item.id) {
        mutableStateOf(item.isFavorite)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${FormatUtils.formatDate(item.timestamp)} • ${
                                FormatUtils.formatFileSize(
                                    item.sizeBytes
                                )
                            }",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                isFavorite = !isFavorite
                                onFavoriteToggle(item)
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = "Favorite",
                                tint = if (isFavorite) {
                                    Color.Red
                                } else {
                                    Color.White
                                }
                            )
                        }
                        /*   IconButton(onClick = { onFavoriteToggle(item) }) {
                               Icon(
                                   imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                   contentDescription = "Favorite",
                                   tint = if (item.isFavorite) Color.Red else Color.White
                               )
                           }*/

                        IconButton(onClick = {
                            ShareUtils.shareRecordItem(context, item)
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = Color.White)
                        }

                        IconButton(onClick = {
                            onDelete(item)
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }
                }

                // Image display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = if (item.uriString.isNotEmpty()) Uri.parse(item.uriString) else File(
                            item.filePath
                        ),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
