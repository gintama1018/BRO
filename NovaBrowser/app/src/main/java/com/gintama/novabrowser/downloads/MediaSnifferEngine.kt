package com.gintama.novabrowser.downloads

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray

/**
 * MediaSnifferEngine: Inspects active WebView DOM for direct media elements
 * (videos, audios, high-res images, PDF documents) and presents a downloadable manifest.
 */
object MediaSnifferEngine {

    enum class MediaType { VIDEO, AUDIO, IMAGE, DOCUMENT }

    data class MediaItem(
        val url: String,
        val type: MediaType,
        val title: String,
        val filename: String
    )

    private const val SNIFFER_JS = """
        (function() {
            try {
                var items = [];
                // 1. Videos & Sources
                var vids = document.querySelectorAll('video, video source');
                for (var i = 0; i < vids.length; i++) {
                    var src = vids[i].src || vids[i].getAttribute('src');
                    if (src && (src.indexOf('http://') === 0 || src.indexOf('https://') === 0)) {
                        items.push({ url: src, type: 'VIDEO', title: document.title || 'Video' });
                    }
                }
                // 2. Audio & Sources
                var auds = document.querySelectorAll('audio, audio source');
                for (var j = 0; j < auds.length; j++) {
                    var asrc = auds[j].src || auds[j].getAttribute('src');
                    if (asrc && (asrc.indexOf('http://') === 0 || asrc.indexOf('https://') === 0)) {
                        items.push({ url: asrc, type: 'AUDIO', title: document.title || 'Audio' });
                    }
                }
                // 3. High-res Images (> 160px)
                var imgs = document.querySelectorAll('img');
                for (var k = 0; k < imgs.length && k < 40; k++) {
                    var isrc = imgs[k].src || imgs[k].getAttribute('src');
                    if (isrc && (isrc.indexOf('http://') === 0 || isrc.indexOf('https://') === 0)) {
                        var w = imgs[k].naturalWidth || imgs[k].width || 0;
                        if (w > 160) {
                            items.push({ url: isrc, type: 'IMAGE', title: imgs[k].alt || 'Image' });
                        }
                    }
                }
                // 4. Downloadable Document/Media Links
                var links = document.querySelectorAll('a[href]');
                for (var l = 0; l < links.length && l < 60; l++) {
                    var href = links[l].href;
                    if (href && href.match(/\.(pdf|zip|mp4|webm|mp3|m4a|apk|tar|gz|iso)($|\?)/i)) {
                        items.push({ url: href, type: 'DOCUMENT', title: links[l].innerText.trim() || 'File' });
                    }
                }
                // Deduplicate
                var seen = {};
                var out = [];
                for (var m = 0; m < items.length; m++) {
                    if (!seen[items[m].url]) {
                        seen[items[m].url] = true;
                        out.push(items[m]);
                    }
                }
                return JSON.stringify(out);
            } catch(e) {
                return '[]';
            }
        })();
    """

    fun sniffMedia(webView: WebView, onResult: (List<MediaItem>) -> Unit) {
        webView.evaluateJavascript(SNIFFER_JS) { jsonRaw ->
            val result = mutableListOf<MediaItem>()
            try {
                val cleanJson = if (jsonRaw != null && jsonRaw.startsWith("\"") && jsonRaw.endsWith("\"")) {
                    // Unescape JSON string returned by evaluateJavascript
                    val unescaped = jsonRaw.substring(1, jsonRaw.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    unescaped
                } else {
                    jsonRaw ?: "[]"
                }

                val array = JSONArray(cleanJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val url = obj.getString("url")
                    val typeStr = obj.optString("type", "DOCUMENT")
                    val title = obj.optString("title", "Media")

                    val type = when (typeStr) {
                        "VIDEO" -> MediaType.VIDEO
                        "AUDIO" -> MediaType.AUDIO
                        "IMAGE" -> MediaType.IMAGE
                        else -> MediaType.DOCUMENT
                    }

                    val filename = URLUtil.guessFileName(url, null, null)
                    result.add(MediaItem(url, type, title, filename))
                }
            } catch (_: Exception) {}
            onResult(result)
        }
    }

    fun showMediaSnifferDialog(activity: Activity, webView: WebView) {
        sniffMedia(webView) { mediaList ->
            activity.runOnUiThread {
                val dialog = BottomSheetDialog(activity)
                val view = LayoutInflater.from(activity).inflate(R.layout.dialog_media_sniffer, null)
                dialog.setContentView(view)

                val tvCount = view.findViewById<TextView>(R.id.tvSnifferCount)
                val rvMedia = view.findViewById<RecyclerView>(R.id.rvSnifferList)
                val layoutEmpty = view.findViewById<View>(R.id.layoutSnifferEmpty)
                val btnDownloadAll = view.findViewById<Button>(R.id.btnSnifferDownloadAll)

                tvCount.text = "${mediaList.size} Media Items Detected"

                if (mediaList.isEmpty()) {
                    rvMedia.visibility = View.GONE
                    layoutEmpty.visibility = View.VISIBLE
                    btnDownloadAll.visibility = View.GONE
                } else {
                    rvMedia.visibility = View.VISIBLE
                    layoutEmpty.visibility = View.GONE
                    btnDownloadAll.visibility = View.VISIBLE

                    rvMedia.layoutManager = LinearLayoutManager(activity)
                    rvMedia.adapter = SnifferAdapter(mediaList) { item ->
                        NovaDownloadEngine.startDownload(
                            context = activity,
                            url = item.url,
                            filename = item.filename,
                            mimeType = null,
                            userAgent = webView.settings.userAgentString,
                            isQuarantine = DownloadHandler.isRiskyExtension(item.filename.substringAfterLast(".", ""))
                        )
                        Toast.makeText(activity, "Downloading: ${item.filename}", Toast.LENGTH_SHORT).show()
                    }

                    btnDownloadAll.setOnClickListener {
                        for (item in mediaList.take(8)) { // Download up to first 8 concurrently
                            NovaDownloadEngine.startDownload(
                                context = activity,
                                url = item.url,
                                filename = item.filename,
                                mimeType = null,
                                userAgent = webView.settings.userAgentString,
                                isQuarantine = DownloadHandler.isRiskyExtension(item.filename.substringAfterLast(".", ""))
                            )
                        }
                        Toast.makeText(activity, "Enqueued ${mediaList.take(8).size} downloads", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }
        }
    }

    private class SnifferAdapter(
        private val items: List<MediaItem>,
        private val onDownload: (MediaItem) -> Unit
    ) : RecyclerView.Adapter<SnifferAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivSnifferIcon)
            val tvName: TextView = view.findViewById(R.id.tvSnifferName)
            val tvType: TextView = view.findViewById(R.id.tvSnifferType)
            val btnDownload: Button = view.findViewById(R.id.btnSnifferDownload)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sniffer_media, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.filename
            holder.tvType.text = item.type.name

            when (item.type) {
                MediaType.VIDEO -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_auto_awesome)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.risk_suspicious))
                }
                MediaType.AUDIO -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_clean_reader)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.accent_emerald))
                }
                MediaType.IMAGE -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_globe)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.incognito_accent))
                }
                MediaType.DOCUMENT -> {
                    holder.ivIcon.setImageResource(R.drawable.ic_bookmark_border)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
                }
            }

            holder.btnDownload.setOnClickListener {
                onDownload(item)
                holder.btnDownload.isEnabled = false
                holder.btnDownload.text = "Queued"
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
