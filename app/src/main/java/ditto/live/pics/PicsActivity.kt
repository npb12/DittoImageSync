package ditto.live.pics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.luck.picture.lib.PictureSelector
import com.luck.picture.lib.config.PictureConfig
import com.luck.picture.lib.config.PictureMimeType
import ditto.live.pics.pictureselector.GlideEngine
import kotlinx.coroutines.*
import live.ditto.*
import live.ditto.android.DefaultAndroidDittoDependencies
import live.ditto.transports.DittoSyncPermissions
import java.io.File


val AppID = "8c85a21f-86e2-43dd-8c3d-df5693002f6e"

class PicsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    var liveQuery: DittoLiveQuery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pics)
        checkPermissions()
        viewManager = GridLayoutManager(this, 3)
        val picsAdapter = PicsAdapter(this)
        viewAdapter = picsAdapter

        recyclerView = findViewById<RecyclerView>(R.id.recyclerView).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        findViewById<FloatingActionButton>(R.id.camera_btn).setOnClickListener {

            PictureSelector.create(this)
                .openCamera(PictureMimeType.ofImage())
                .loadImageEngine(GlideEngine.createGlideEngine())
                .imageFormat(PictureMimeType.MIME_TYPE_IMAGE)
                .imageFormat(PictureMimeType.JPEG)
                .forResult(PictureConfig.REQUEST_CAMERA)
        }

        try {
            DittoLogger.minimumLogLevel = DittoLogLevel.VERBOSE
            val androidDependencies = DefaultAndroidDittoDependencies(applicationContext)
            DittoHandler.ditto = Ditto(
                androidDependencies,
                DittoIdentity.OfflinePlayground(androidDependencies, AppID)
            )
            DittoHandler.ditto?.setOfflineOnlyLicenseToken("your_key")
        } catch (e: Exception) {
            Log.e(e.message, e.localizedMessage)
        }
        DittoHandler.ditto?.tryStartSync()

        liveQuery = Pic.observePics { pics, event ->
            val adapter = (this.viewAdapter as PicsAdapter)
            when (event) {
                is DittoLiveQueryEvent.Initial -> {
                    runOnUiThread {
                        adapter.setInitial(pics.toMutableList())
                    }
                }
                is DittoLiveQueryEvent.Update -> {
                    runOnUiThread {
                        adapter.set(pics)
                        adapter.inserts(event.insertions)
                        adapter.deletes(event.deletions)
                        adapter.updates(event.updates)
                        adapter.moves(event.moves)
                    }
                }
            }
        }

        picsAdapter.onItemClick = { it ->
            val intent = Intent(this, PicDetailActivity::class.java)
            intent.putExtra("picId", it.id)
            intent.putExtra("isVideo", it.mimetype == "video/mp4")
            startActivity(intent)
        }

        if (prefs.userName == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    fun checkPermissions() {
        val missing = DittoSyncPermissions(this).missingPermissions()
        if (missing.isNotEmpty()) {
            this.requestPermissions(missing, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Regardless of the outcome, tell Ditto that permissions maybe changed
        DittoHandler.ditto?.refreshPermissions()
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        R.id.delete -> {
            Pic.deleteAll()
            true
        }

        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PictureConfig.CHOOSE_REQUEST, PictureConfig.REQUEST_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        val selectList = PictureSelector.obtainMultipleResult(data)
                        if (selectList.size > 0) {
                            val item = selectList.get(0)
                            Pic.insertPicture(this, File(item.realPath))
                        }
                    }
                }
            }
        }
    }

    class PicsAdapter(context: Context): RecyclerView.Adapter<PicsAdapter.PicViewHolder>() {
        private val pics = mutableListOf<Pic>()

        private var context: Context

        var onItemClick: ((Pic) -> Unit)? = null

        init {
            this.context = context
        }

            class PicViewHolder(v: View): RecyclerView.ViewHolder(v) {
                var thumbnailFetcher: DittoAttachmentFetcher? = null
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PicViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.pic_view, parent, false)
            return PicViewHolder(view)
        }

        override fun onBindViewHolder(holder: PicViewHolder, position: Int) {
            val pic = pics[position]
            configureWithPic(holder, pic)
            holder.itemView.setOnClickListener {
                onItemClick?.invoke(pics[holder.adapterPosition])
            }
        }

        fun configureWithPic(holder: PicViewHolder, pic: Pic) {
            val imageView = holder.itemView.findViewById<AppCompatImageView>(R.id.imageView)
            val progressView = holder.itemView.findViewById<ProgressBar>(R.id.progress)
            if (pic.imageAttachmentToken == null) {
                imageView.setImageDrawable(null)
                return
            }

            if (pic.mimetype == "image/jpeg") {
                holder.thumbnailFetcher = Pic.getDataForAttachmentToken(
                    context,
                    pic.imageAttachmentToken!!
                ) { progress, error, byteData, file ->
                    GlobalScope.launch(Dispatchers.Main) {

                        if (progress == null || progress == 0f || progress == 1f) {
                            progressView.visibility = View.GONE
                        } else {
                            progressView.visibility = View.VISIBLE
                        }

                        if (progress != null) {
                            progressView.progress = progress.toInt()
                        } else {
                            progressView.progress = 0
                        }

                        if (byteData != null) {
                            Glide.with(context)
                                .asBitmap()
                                .load(byteData)
                                .into(imageView)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = this.pics.size

        fun pics(): List<Pic> {
            return this.pics.toList()
        }

        fun set(tasks: List<Pic>): Int {
            this.pics.clear()
            this.pics.addAll(tasks)
            return this.pics.size
        }

        fun inserts(indexes: List<Int>): Int {
            for (index in indexes) {
                this.notifyItemRangeInserted(index, 1)
            }
            return this.pics.size
        }

        fun deletes(indexes: List<Int>): Int {
            for (index in indexes) {
                this.notifyItemRangeRemoved(index, 1)
            }
            return this.pics.size
        }

        fun updates(indexes: List<Int>): Int {
            for (index in indexes) {
                this.notifyItemRangeChanged(index, 1)
            }
            return this.pics.size
        }

        fun moves(moves: List<DittoLiveQueryMove>) {
            for (move in moves) {
                this.notifyItemMoved(move.from, move.to)
            }
        }

        fun setInitial(pics: List<Pic>): Int {
            this.pics.addAll(pics)
            this.notifyDataSetChanged()
            return this.pics.size
        }
    }
}
