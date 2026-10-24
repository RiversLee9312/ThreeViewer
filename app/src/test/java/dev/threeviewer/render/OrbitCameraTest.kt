package dev.threeviewer.render

import org.junit.Assert.*
import org.junit.Test

class OrbitCameraTest {
    @Test fun fitPreservesZoomAcrossOrientationAndResetUsesNewFit() {
        val camera = OrbitCamera()
        camera.fit(400, 800, 1.4)
        val portrait = camera.distance
        camera.zoom(2.0)
        camera.fit(800, 400, 1.4)
        assertEquals(200, camera.zoomPercent())
        camera.reset()
        assertEquals(100, camera.zoomPercent())
        assertTrue(camera.distance < portrait)
    }
    @Test fun zoomAndPitchAreBounded() {
        val camera = OrbitCamera()
        camera.zoom(1e20)
        assertEquals(.65, camera.distance, .00001)
        camera.zoom(1e-20)
        assertEquals(20.0, camera.distance, .00001)
        camera.rotate(10000.0, 10000.0)
        assertTrue(camera.eye().all { it.isFinite() })
        assertEquals(1.45, camera.pitch, .00001)
    }
    @Test fun resetRestoresAllCameraState() {
        val camera = OrbitCamera()
        val home = camera.eye()
        camera.pan(.5, -.2); camera.rotate(2.0, -.5); camera.zoom(2.0)
        camera.reset()
        assertArrayEquals(home, camera.eye(), .00001)
    }
}
