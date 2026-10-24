package dev.threeviewer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ModelSource(val uri: String, val name: String, val size: Long = 0)
data class LoadedModel(val source: ModelSource, val bytes: ByteArray, val info: ModelInfo)
class ModelTooLarge : Exception()
class InvalidModel(cause: Exception) : Exception(cause)

class ModelStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("viewer", Context.MODE_PRIVATE)

    fun recent(): List<ModelSource> = runCatching {
        val arr = JSONArray(preferences.getString("recent", "[]"))
        List(arr.length()) { i -> arr.getJSONObject(i).let { ModelSource(it.getString("uri"), it.getString("name"), it.optLong("size")) } }
    }.getOrDefault(emptyList())

    fun remember(source: ModelSource) {
        val old = recent().filter { it.uri != source.uri }
        save(listOf(source) + old.take(11))
        old.drop(11).forEach { release(it.uri) }
    }

    fun remove(uri: String) { save(recent().filter { it.uri != uri }); release(uri) }
    fun clear() { recent().forEach { release(it.uri) }; save(emptyList()) }
    private fun release(uri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    private fun save(list: List<ModelSource>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("size", it.size)) }
        preferences.edit().putString("recent", arr.toString()).apply()
    }

    suspend fun load(uri: Uri?, sampleName: String): LoadedModel = withContext(Dispatchers.IO) {
        var name = sampleName
        var size = 0L
        if (uri != null) {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0) ?: "model.glb"
                    if (!it.isNull(1)) size = it.getLong(1)
                }
            }
        }
        if (size > MAX_MODEL_BYTES) throw ModelTooLarge()
        val input = if (uri == null) context.assets.open("knot.glb")
            else context.contentResolver.openInputStream(uri) ?: error("Unavailable URI")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(chunk)
                if (count == -1) break
                if (out.size().toLong() + count > MAX_MODEL_BYTES) throw ModelTooLarge()
                out.write(chunk, 0, count)
            }
            out.toByteArray()
        }
        val info = try { GlbDocument.inspect(bytes) } catch (e: Exception) { throw InvalidModel(e) }
        LoadedModel(ModelSource(uri?.toString() ?: "sample:knot", name, bytes.size.toLong()), bytes, info)
    }

    var theme: Int
        get() = preferences.getInt("theme", 0)
        set(value) { preferences.edit().putInt("theme", value).apply() }
    var dynamic: Boolean
        get() = preferences.getBoolean("dynamic", true)
        set(value) { preferences.edit().putBoolean("dynamic", value).apply() }
}
