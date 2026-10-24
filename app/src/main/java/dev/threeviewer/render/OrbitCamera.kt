package dev.threeviewer.render

import kotlin.math.*

/** Camera state is independent of rendering, so touch, keyboard and accessibility share one implementation. */
class OrbitCamera {
    var changed: () -> Unit = {}
    var yaw = .55; private set
    var pitch = .24; private set
    var distance = 4.5; private set
    private var homeDistance = 4.5
    var panX = 0.0; private set
    var panY = 0.0; private set
    var panZ = 0.0; private set
    fun fit(width: Int, height: Int, radius: Double) {
        if (width <= 0 || height <= 0) return
        val halfFov = min(atan(12.0 / 28.0), atan(12.0 / 28.0 * width / height))
        val fitted = (radius / sin(halfFov) * 1.08).coerceIn(2.0, 20.0)
        distance = (distance / homeDistance * fitted).coerceIn(.65, 20.0)
        homeDistance = fitted
        changed()
    }
    fun zoomPercent() = (100 * homeDistance / distance).roundToInt()
    fun rotate(dx: Double, dy: Double) { yaw = (yaw + dx) % (2 * PI); pitch = (pitch + dy).coerceIn(-1.45, 1.45); changed() }
    fun zoom(factor: Double) { if (factor.isFinite() && factor > 0) { distance = (distance / factor).coerceIn(.65, 20.0); changed() } }
    fun pan(dx: Double, dy: Double) {
        panX = (panX + distance * (dx * cos(yaw) - dy * sin(yaw) * sin(pitch))).coerceIn(-10.0, 10.0)
        panY = (panY + distance * dy * cos(pitch)).coerceIn(-10.0, 10.0)
        panZ = (panZ + distance * (-dx * sin(yaw) - dy * cos(yaw) * sin(pitch))).coerceIn(-10.0, 10.0)
        changed()
    }
    fun reset() { yaw = .55; pitch = .24; distance = homeDistance; panX = 0.0; panY = 0.0; panZ = 0.0; changed() }
    fun eye(): DoubleArray = doubleArrayOf(panX + distance * cos(pitch) * sin(yaw), panY + distance * sin(pitch), panZ + distance * cos(pitch) * cos(yaw))
}
