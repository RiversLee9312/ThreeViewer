package dev.threeviewer.data

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val MAX_MODEL_BYTES = 200 * 1024 * 1024

data class ModelInfo(
    val meshes: Int, val triangles: Long, val materials: Int,
    val animations: List<String>, val bytes: Int
)

/** Validate the container before invoking native gltfio. External GLB resources are intentionally rejected. */
object GlbDocument {
    fun inspect(bytes: ByteArray): ModelInfo {
        require(bytes.size in 20..MAX_MODEL_BYTES) { "Invalid size" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.int == 0x46546C67 && buffer.int == 2) { "GLB 2.0 required" }
        require(buffer.int.toLong() == bytes.size.toLong()) { "Truncated GLB" }
        var json: JSONObject? = null
        var first = true
        while (buffer.remaining() >= 8) {
            val length = buffer.int
            val type = buffer.int
            require(length >= 0 && length % 4 == 0 && length <= buffer.remaining()) { "Invalid chunk" }
            if (first) require(type == 0x4E4F534A) { "JSON must be first" }
            if (type == 0x4E4F534A) {
                require(json == null) { "Duplicate JSON" }
                val content = ByteArray(length)
                buffer.get(content)
                json = JSONObject(content.toString(Charsets.UTF_8).trimEnd(' ', '\u0000'))
            } else buffer.position(buffer.position() + length)
            first = false
        }
        require(buffer.remaining() == 0) { "Trailing bytes" }
        val root = requireNotNull(json)
        require(root.getJSONObject("asset").getString("version") == "2.0")
        listOf("buffers", "images").forEach { key ->
            root.optJSONArray(key)?.let { arr ->
                for (i in 0 until arr.length()) {
                    val uri = arr.getJSONObject(i).optString("uri")
                    require(uri.isEmpty() || uri.startsWith("data:")) { "External resources" }
                }
            }
        }
        val meshes = root.optJSONArray("meshes")
        var triangles = 0L
        val accessors = root.optJSONArray("accessors")
        for (i in 0 until (meshes?.length() ?: 0)) {
            val primitives = meshes!!.getJSONObject(i).getJSONArray("primitives")
            for (j in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(j)
                val index = if (primitive.has("indices")) primitive.getInt("indices")
                    else primitive.getJSONObject("attributes").optInt("POSITION", -1)
                val count = accessors?.optJSONObject(index)?.optLong("count", 0) ?: 0
                triangles += when (primitive.optInt("mode", 4)) {
                    4 -> count / 3
                    5, 6 -> (count - 2).coerceAtLeast(0)
                    else -> 0
                }
            }
        }
        val animations = root.optJSONArray("animations")
        return ModelInfo(meshes?.length() ?: 0, triangles, root.optJSONArray("materials")?.length() ?: 0,
            List(animations?.length() ?: 0) { animations!!.getJSONObject(it).optString("name") }, bytes.size)
    }
}
