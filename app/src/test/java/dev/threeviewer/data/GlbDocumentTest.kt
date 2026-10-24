package dev.threeviewer.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbDocumentTest {
    private fun glb(json: String): ByteArray {
        val content = json.padEnd((json.length + 3) / 4 * 4, ' ').toByteArray()
        return ByteBuffer.allocate(20 + content.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546C67).putInt(2).putInt(20 + content.size)
            .putInt(content.size).putInt(0x4E4F534A).put(content).array()
    }
    @Test fun countsTrianglesAndAnimations() {
        val model = glb("""{"asset":{"version":"2.0"},"accessors":[{"count":12},{"count":8}],"meshes":[{"primitives":[{"indices":0},{"mode":5,"attributes":{"POSITION":1}}]}],"animations":[{"name":"Spin"}]}""")
        val info = GlbDocument.inspect(model)
        assertEquals(1, info.meshes)
        assertEquals(10L, info.triangles)
        assertEquals(listOf("Spin"), info.animations)
    }
    @Test fun rejectsTruncation() {
        val model = glb("""{"asset":{"version":"2.0"}}""")
        assertThrows(IllegalArgumentException::class.java) { GlbDocument.inspect(model.copyOf(model.size - 1)) }
    }
    @Test fun rejectsExternalResources() {
        assertThrows(IllegalArgumentException::class.java) {
            GlbDocument.inspect(glb("""{"asset":{"version":"2.0"},"images":[{"uri":"texture.png"}]}"""))
        }
    }
    @Test fun acceptsEmbeddedDataUris() {
        assertEquals(0, GlbDocument.inspect(glb("""{"asset":{"version":"2.0"},"images":[{"uri":"data:image/png;base64,abc"}]}""")).meshes)
    }
    @Test fun rejectsNegativeChunkLength() {
        val model = glb("""{"asset":{"version":"2.0"}}""")
        ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN).putInt(12, -4)
        assertThrows(IllegalArgumentException::class.java) { GlbDocument.inspect(model) }
    }
}
