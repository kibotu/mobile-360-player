package com.exozet.threehundredsixty.player

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.asha.vrlib.MDVRLibrary
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.android.synthetic.main.threehundredsixty_view.view.*
import java.util.*


class ThreeHundredSixtyPlayer @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val TAG by lazy { "${this::class.java.simpleName}:$uuid" }

    private val uuid: String by lazy { UUID.randomUUID().toString().take(8) }

    private var vrLibrary: MDVRLibrary? = null

    var onCameraRotation: ((pitch: Float, yaw: Float, roll: Float) -> Unit)? = null

    var debug = true

    private fun log(message: String) {
        if (debug)
            Log.d(TAG, message)
    }

    var uri: Uri? = null
        set(value) {
            if (field == value)
                return

            field = when {
                value.toString().startsWith("http://") -> value
                value.toString().startsWith("https://") -> value
                value.toString().startsWith("file:///") -> value
                else -> parseFile(value.toString())
            }

            if (vrLibrary == null && (value.toString().startsWith("http://") || value.toString().startsWith("https://")))
                initVRLibrary()

            vrLibrary?.notifyPlayerChanged()
        }

    @ProjectionMode
    var projectionMode: Int = PROJECTION_MODE_SPHERE
        set(value) {
            field = value
            vrLibrary?.switchDisplayMode(context, projectionMode)
            vrLibrary?.notifyPlayerChanged()
        }

    @InteractionMode
    var interactionMode: Int = INTERACTIVE_MODE_TOUCH
        set(value) {
            field = value
            motionSwitch.isChecked = value == INTERACTIVE_MODE_MOTION_WITH_TOUCH
            vrLibrary?.switchInteractiveMode(context, value)
            vrLibrary?.notifyPlayerChanged()
        }

    var showControls: Boolean = false
        set(value) {
            field = value
            motionSwitch.goneUnless(!value)
        }

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.threehundredsixty_view, this, true)
    }

    private var pitch = 0f
    private var yaw = 0f
    private var roll = 0f

    private fun initVRLibrary() {

        if (uri == null) {
            Log.e(TAG, "Please provide com.exozet.freedomplayer.Parameter#threeHundredSixtyUri")
            return
        }

        log("uri=$uri")

        loadImage(uri) { bitmap ->

            vrLibrary = MDVRLibrary.with(context)
                    .displayMode(projectionMode)
                    .interactiveMode(interactionMode)
                    .pinchEnabled(true)
                    .asBitmap {

                        it.texture(bitmap)

                        vrLibrary?.onTextureResize(bitmap?.width?.toFloat()
                                ?: width.toFloat(), bitmap?.height?.toFloat() ?: height.toFloat())

                        vrLibrary?.onResume(context)

                        cancelBusy()
                    }
                    .build(glView)

            vrLibrary?.setDirectorFilter(object : MDVRLibrary.IDirectorFilter {
                override fun onFilterPitch(p0: Float): Float {
                    pitch = p0
                    onCameraRotation?.invoke(pitch, yaw, roll)
                    return p0
                }

                override fun onFilterYaw(p0: Float): Float {
                    yaw = p0
                    onCameraRotation?.invoke(pitch, yaw, roll)
                    return p0
                }

                override fun onFilterRoll(p0: Float): Float {
                    roll = p0
                    onCameraRotation?.invoke(pitch, yaw, roll)
                    return p0
                }
            })

            glView.visibility = View.VISIBLE
        }
    }

    private fun onCreate() {
        initVRLibrary()
        motionSwitch.setOnCheckedChangeListener { _, isChecked -> interactionMode = if (isChecked) INTERACTIVE_MODE_MOTION_WITH_TOUCH else INTERACTIVE_MODE_TOUCH }
    }

    private fun busy() {
        progress.visibility = View.VISIBLE
    }

    private fun cancelBusy() {
        progress.visibility = View.GONE
    }

    private fun onResume() {
        vrLibrary?.onResume(context)
    }

    private fun onPause() {
        vrLibrary?.onPause(context)
    }

    private fun onDestroy() {
        vrLibrary?.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        vrLibrary?.onOrientationChanged(context)
    }

    private fun loadImage(uri: Uri?, block: (bitmap: Bitmap?) -> Unit) {

        Glide.with(this)
                .asBitmap()
                .load(uri)
                .apply(RequestOptions
                        .fitCenterTransform()
                        .priority(Priority.HIGH)
                        .dontAnimate()
                        .skipMemoryCache(false)
                        .override(2048, 2048)
                        .diskCacheStrategy(DiskCacheStrategy.ALL))
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        block(resource)
                    }
                })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        busy()
        onCreate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onDestroy()
        cancelBusy()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) onResume() else onPause()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) onResume() else onPause()
    }

    companion object {
        const val PROJECTION_MODE_SPHERE = MDVRLibrary.PROJECTION_MODE_SPHERE
        const val PROJECTION_MODE_MULTI_FISH_EYE_HORIZONTAL = MDVRLibrary.PROJECTION_MODE_MULTI_FISH_EYE_HORIZONTAL
        const val PROJECTION_MODE_MULTI_FISH_EYE_VERTICAL = MDVRLibrary.PROJECTION_MODE_MULTI_FISH_EYE_VERTICAL
        const val INTERACTIVE_MODE_TOUCH = MDVRLibrary.INTERACTIVE_MODE_TOUCH
        const val INTERACTIVE_MODE_MOTION = MDVRLibrary.INTERACTIVE_MODE_MOTION
        const val INTERACTIVE_MODE_MOTION_WITH_TOUCH = MDVRLibrary.INTERACTIVE_MODE_MOTION_WITH_TOUCH
        internal const val SHOW_CONTROLS = "SHOW_CONTROLS"

        internal fun View.goneUnless(isGone: Boolean = true) {
            visibility = if (isGone) View.GONE else View.VISIBLE
        }
    }
}