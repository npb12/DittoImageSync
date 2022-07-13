package ditto.live.pics

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.preference.PreferenceManager
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import live.ditto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


val TAG = "Pic"
val collectionName = "pics"

class Pic(document: DittoDocument){
    var id: String
    var imageAttachmentToken: DittoAttachmentToken? = null
    var username: String
    var mimetype: String
    var timestamp: LocalDateTime

    init {

        id = document.id.toString()
        imageAttachmentToken = document["image"].attachmentToken
        username = document["username"].stringValue
        timestamp = LocalDateTime.parse(document["timestamp"].stringValue, formatter)
        mimetype = document["mimeType"].string ?: "image/jpeg"
    }

    companion object {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)

        fun insertPicture(context: Context, file: File) {
            //val stream = compressToInputStream(f)
            runBlocking {
                val compressedImageFile = Compressor.compress(context, file) {
                    default(width = 100, height = 100, quality = 60, format = Bitmap.CompressFormat.WEBP)
                }
                val store = DittoHandler.ditto?.store
                val attachment = store?.collection(collectionName)?.newAttachment(compressedImageFile.path)
                store?.collection(collectionName)?.upsert(
                    mapOf(
                        "image" to attachment,
                        "mimeType" to "image/jpeg",
                        "timestamp" to LocalDateTime.now().format(formatter)
                    )
                )
            }
        }

        fun compressToInputStream(file: File): ByteArrayInputStream {
            val bitmap = BitmapFactory.decodeFile(file.path)
            val stream = ByteArrayOutputStream()
            val matrix = Matrix()
            //matrix.postRotate(-bitmap.rotationDegrees.toFloat())
            val w = bitmap.width
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                200,
                200,
                true
            )
            val rotatedBitmap =
                Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, matrix, true)
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
            return ByteArrayInputStream(stream.toByteArray())
        }

        fun deletePictureById(id: String) {
            val store = DittoHandler.ditto?.store
            store?.collection(collectionName)?.findByID(DittoDocumentID(id))?.remove()
        }

        fun deleteAll() {
            DittoHandler.ditto?.store?.collection(collectionName)?.findAll()?.remove()
        }

        fun getDataForAttachmentToken(
            context: Context,
            token: DittoAttachmentToken,
            callback: (progress: Float?, error: Exception?, byteData: ByteArray?, file: File?) -> Unit
        ): DittoAttachmentFetcher? {
            return DittoHandler.ditto?.store?.collection(collectionName)?.fetchAttachment(token) { event ->
                when (event.type) {
                    DittoAttachmentFetchEventType.Completed -> {
                        val data = event.asCompleted()?.attachment?.getData()
                        if (data != null) {
                            try {
                                val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
                                file.writeBytes(data)
                                callback(1f, null, data, file)
                            } catch (e: Exception) {
                                callback(null, e, null, null)
                            }
                        } else {
                            callback(null, Exception("Attachment data is null"), null, null)
                        }
                    }
                    DittoAttachmentFetchEventType.Progress -> {
                        val downloadedBytes = event.asProgress()?.downloadedBytes
                        val totalBytes = event.asProgress()?.totalBytes
                        if (downloadedBytes != null && totalBytes != null) {
                            callback(
                                downloadedBytes.toFloat() / totalBytes.toFloat(),
                                null,
                                null,
                                null
                            )
                        }
                    }
                    else -> {
                        callback(null, Exception("Unknown error"), null, null)
                    }
                }
            }
        }

        fun observePics(callback: (pics: List<Pic>, event: DittoLiveQueryEvent) -> Unit): DittoLiveQuery? {
            return DittoHandler.ditto?.store?.collection(collectionName)?.findAll()
                ?.sort("timestamp", DittoSortDirection.Descending)
                ?.observe{ docs, event ->
                    val pics = docs.map { Pic(it) }
                    callback(pics, event)
                }
        }

        fun observePicById(picId: String, callback: (pic: Pic?) -> Unit): DittoLiveQuery? {
            return DittoHandler.ditto?.store?.collection(collectionName)?.findByID(DittoDocumentID(picId))
                ?.observe{ doc, n ->
                    if (doc == null) {
                        callback(null)
                    } else {
                        val pic = Pic(doc)
                        callback(pic)
                    }
                }
        }
    }
}