package ditto.live.pics

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import live.ditto.DittoAttachmentFetcher
import live.ditto.DittoLiveQuery
import java.time.format.DateTimeFormatter
import java.util.*


class PicDetailActivity : AppCompatActivity() {

    var picId: String? = null
    private lateinit var imageView: AppCompatImageView

    private lateinit var usernameTextView: AppCompatTextView
    private lateinit var dateTextView: AppCompatTextView
    private lateinit var sizeTextView: AppCompatTextView

    var liveQuery: DittoLiveQuery? = null
    var thumbnailFetcher: DittoAttachmentFetcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pic_details)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        imageView = findViewById(R.id.imageView)
        usernameTextView = findViewById(R.id.username)
        dateTextView = findViewById(R.id.date)
        sizeTextView = findViewById(R.id.size)

        picId = intent.getStringExtra("picId")

        findViewById<AppCompatButton>(R.id.delete).setOnClickListener {
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Are you sure you want to delete this?")
            alert.setNegativeButton("Yes, Delete it"){ dialog, which ->
                picId?.let { it1 -> Pic.deletePictureById(it1) }
                dialog.dismiss()
                finish()
            }
            alert.setNeutralButton("No, cancel") { dialog, which ->
                dialog.cancel()
            }

            val dialog = alert.create()
            dialog.show()
        }

        liveQuery = picId?.let {
            Pic.observePicById(it) { pic ->
                if (pic == null) {
                    finish()
                    return@observePicById
                }

                if (pic.imageAttachmentToken == null) {
                    imageView.setImageDrawable(null)
                    return@observePicById
                }

                usernameTextView.text = String.format("Username: %s", pic.username)
                val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy h:mm a", Locale.ENGLISH)
                dateTextView.text = pic.timestamp.format(formatter)


                if (pic.mimetype == "image/jpeg") {
                    thumbnailFetcher = Pic.getDataForAttachmentToken(
                        this,
                        pic.imageAttachmentToken!!
                    ) { progress, error, byteData, file ->
                        GlobalScope.launch(Dispatchers.Main) {
                            if (byteData != null) {
                                Glide.with(this@PicDetailActivity)
                                    .asBitmap()
                                    .load(byteData)
                                    .into(imageView)

                                val file_size: Int = java.lang.String.valueOf(byteData.size / 1024).toInt()
                                sizeTextView.text = String.format("Size: %d", file_size)
                            }
                        }
                    }
                }
            }
        }
    }
}