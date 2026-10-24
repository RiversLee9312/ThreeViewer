package dev.threeviewer.render

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.*
import android.view.GestureDetector
import com.google.android.filament.*
import com.google.android.filament.utils.*
import dev.threeviewer.data.LoadedModel
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.roundToInt

/** Filament and all native resources live on the UI thread. No OpenGL fallback is used. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class VulkanModelView(context: Context, private val model: LoadedModel,
    val orbit: OrbitCamera, private val onReady: () -> Unit, private val onFailure: () -> Unit,
    private val onCameraChanged: (Int) -> Unit
) : SurfaceView(context), Choreographer.FrameCallback {
    private var viewer: ModelViewer? = null
    private var ibl: IndirectLight? = null
    private var active = false
    private var scheduled = false
    private var settlingFrames = 4
    private var ready = false
    private var lastFrame = 0L
    private var elapsed = 0.0
    var playing = false
    var animationIndex = 0
        set(value) { if (field != value) elapsed = 0.0; field = value }
    private var background = intArrayOf(242, 240, 247)
    private var radius = 1.732
    private var exposure = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var previousCount = 0
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { orbit.zoom(detector.scaleFactor.toDouble()); return true }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean { orbit.reset(); return true }
    })

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        orbit.changed = {
            settlingFrames = 4; requestFrame()
            onCameraChanged(orbit.zoomPercent())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            Utils.init()
            val engine = Engine.create(Engine.Backend.VULKAN)
            Log.i("ThreeViewer", "Renderer backend: ${engine.backend}")
            val mv = ModelViewer(this, engine, manipulator = null)
            viewer = mv
            // Surface callbacks can arrive after View layout and the last scheduled frame.
            holder.addCallback(object : SurfaceHolder.Callback2 {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    orbit.fit(width, height, radius)
                    settlingFrames = 4
                    requestFrame()
                }
                override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
                    settlingFrames = 4
                    requestFrame()
                }
            })
            mv.autoPlayAnimations = false
            mv.view.apply {
                antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
                isPostProcessingEnabled = true
                blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
                ambientOcclusionOptions = ambientOcclusionOptions.apply { enabled = true }
            }
            val env = context.assets.open("studio_ibl.ktx").use { ByteBuffer.wrap(it.readBytes()) }
            val cubemap = KTX1Loader.createTexture(engine, env)
            mv.indirectLightCubemap = cubemap
            val sh = FloatArray(27).apply { this[0] = .8f; this[1] = .8f; this[2] = .82f; this[6] = .15f; this[7] = .15f; this[8] = .15f }
            ibl = IndirectLight.Builder().reflections(cubemap).irradiance(3, sh).intensity(28_000f).build(engine)
            mv.scene.indirectLight = ibl
            engine.lightManager.setDirection(engine.lightManager.getInstance(mv.light), -.4f, -1f, -.6f)
            engine.lightManager.setIntensity(engine.lightManager.getInstance(mv.light), 80_000f)
            mv.loadModelGlb(ByteBuffer.wrap(model.bytes))
            check(mv.asset != null && mv.asset!!.renderableEntities.isNotEmpty()) { "No renderable scene" }
            val ext = mv.asset!!.boundingBox.halfExtent
            check(ext.all { it.isFinite() } && ext.max() > 0f) { "Invalid bounds" }
            radius = kotlin.math.sqrt(ext.sumOf { (it / ext.max()).toDouble().pow(2) })
            mv.transformToUnitCube(Float3(0f, 0f, 0f))
            orbit.fit(width, height, radius)
            updateBackground()
            setActive(true)
        } catch (e: Exception) {
            Log.e("ThreeViewer", "Vulkan initialization or model loading failed", e)
            post(onFailure)
        }
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        lastFrame = 0
        if (value && viewer != null) { settlingFrames = 4; requestFrame() }
        else { Choreographer.getInstance().removeFrameCallback(this); scheduled = false }
    }

    private fun requestFrame() {
        if (active && !scheduled && viewer != null) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        orbit.fit(w, h, radius)
        settlingFrames = 4
        requestFrame()
    }

    fun configure(backgroundRgb: IntArray, ev: Float, play: Boolean, animation: Int) {
        val changed = !background.contentEquals(backgroundRgb) || exposure != ev || playing != play || animationIndex != animation
        if (playing != play) lastFrame = 0L
        background = backgroundRgb
        exposure = ev
        playing = play
        animationIndex = animation
        updateBackground()
        if (changed) { settlingFrames = 4; requestFrame() }
    }

    private fun updateBackground() {
        val mv = viewer ?: return
        mv.scene.skybox = null
        mv.renderer.clearOptions = mv.renderer.clearOptions.apply {
            clear = true
            clearColor = doubleArrayOf(background[0] / 255.0, background[1] / 255.0, background[2] / 255.0, 1.0)
        }
        mv.camera.setExposure(16f, 1f / 125f, 100f * 2.0.pow(exposure.toDouble()).toFloat())
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!active) return
        val mv = viewer ?: return
        try {
            if (playing && lastFrame != 0L) elapsed += ((frameTimeNanos - lastFrame) / 1e9).coerceIn(0.0, .1)
            lastFrame = frameTimeNanos
            val eye = orbit.eye()
            mv.camera.lookAt(eye[0], eye[1], eye[2], orbit.panX, orbit.panY, orbit.panZ, 0.0, 1.0, 0.0)
            mv.animator?.let { animator ->
                if (animationIndex < animator.animationCount) {
                    animator.applyAnimation(animationIndex, elapsed.toFloat())
                    animator.updateBoneMatrices()
                }
            }
            val rendered = mv.render(frameTimeNanos)
            if (!ready && rendered && mv.progress >= 1f) { ready = true; post(onReady) }
            if (rendered) settlingFrames--
            if (!ready || playing || settlingFrames > 0) requestFrame()
        } catch (e: Exception) {
            Log.e("ThreeViewer", "Render failed", e)
            setActive(false)
            post(onFailure)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        scale.onTouchEvent(event)
        val count = event.pointerCount
        val x = (0 until count).sumOf { event.getX(it).toDouble() }.toFloat() / count
        val y = (0 until count).sumOf { event.getY(it).toDouble() }.toFloat() / count
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> if (previousCount == count) {
                if (count == 1 && !scale.isInProgress) orbit.rotate(-(x - previousX) * .008, (y - previousY) * .008)
                else if (count == 2) orbit.pan(-(x - previousX) / width.toDouble(), (y - previousY) / height.toDouble())
            }
            MotionEvent.ACTION_UP -> { performClick(); parent?.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        previousX = x; previousY = y; previousCount = count
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDetachedFromWindow() {
        setActive(false)
        orbit.changed = {}
        // ModelViewer's detach listener destroys its owned resources and engine after this callback.
        viewer?.let { mv ->
            mv.scene.skybox = null; mv.scene.indirectLight = null
            ibl?.let { mv.engine.destroyIndirectLight(it) }
        }
        ibl = null; viewer = null
        super.onDetachedFromWindow()
    }
}
