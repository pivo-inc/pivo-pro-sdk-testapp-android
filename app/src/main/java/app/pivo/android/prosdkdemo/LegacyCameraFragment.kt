package app.pivo.android.sdkdemo.pro

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import app.pivo.android.prosdkdemo.CameraBaseFragment
import app.pivo.android.prosdkdemo.util.ImageUtils
import kotlinx.android.synthetic.main.fragment_camera_base.*
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList


class LegacyCameraFragment : CameraBaseFragment() {

    private val TAG = "LegacyCameraFragment"

    /** Conversion from screen rotation to JPEG orientation.  */
    private val ORIENTATIONS = SparseIntArray()

    private var isFrontCamera = true

    private lateinit var camera: Camera
    private var imageListener: Camera.PreviewCallback? = Camera.PreviewCallback() { bytes: ByteArray, camera: Camera ->
        run {
            onProcessingFrame(bytes, desiredSize.width, desiredSize.height, getRotation(), isFrontCamera)

            camera.addCallbackBuffer(bytes)
        }
    }

    private var desiredSize: Size = Size(640, 480)

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private val MINIMUM_PREVIEW_SIZE = 320

    private var availableSurfaceTexture: SurfaceTexture? = null

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int) {
            availableSurfaceTexture = texture
            startCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.e(TAG, "onViewCreated")

        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        setOrientationListener(true)
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (texture.isAvailable) {
            startCamera()
        } else {
            texture.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        setOrientationListener(false)

        stopCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /** Starts a background thread and its [Handler].  */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread!!.start()
    }

    /** Stops the background thread and its [Handler].  */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Exception!")
        }
    }

    override fun switchCamera(){
        super.switchCamera()

        isFrontCamera = !isFrontCamera

        stopCamera()

        startCamera()
        restart()
    }

    private fun startCamera() {
        val index = getCameraId()

        camera = Camera.open(index)
        try {
            val parameters: Camera.Parameters = camera.parameters
            val focusModes: List<String> = parameters.supportedFocusModes
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            val cameraSizes: List<Camera.Size> = parameters.supportedPreviewSizes

            val sizes: ArrayList<Size> = arrayListOf<Size>()

            for (size in cameraSizes) {
                sizes.add(Size(size.width, size.height))
            }

            val previewSize: Size? = chooseOptimalSize(
                sizes, desiredSize.width, desiredSize.height)

            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.width, previewSize.height)
            }
            camera.setDisplayOrientation(getCameraDisplayOrientation(index))
            camera.parameters = parameters
            camera.setPreviewTexture(availableSurfaceTexture)
        } catch (exception: IOException) {
            camera.release()
        }

        camera.setPreviewCallbackWithBuffer(imageListener)

        val s: Camera.Size = camera.parameters.previewSize
        camera.addCallbackBuffer(ByteArray(ImageUtils.getYUVByteSize(s.height, s.width)))
        texture.setAspectRatio(s.height, s.width)
        camera.startPreview()
    }

    private fun stopCamera() {
        camera.stopPreview()
        camera.setPreviewCallback(null)
        camera.release()
    }

    fun getCameraDisplayOrientation(cameraId:Int): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation: Int = requireActivity().windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        return result
    }

    private fun getCameraId(): Int {
        val ci = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, ci)

            if ((ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK && !isFrontCamera)|| (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && isFrontCamera)) return i
        }
        return -1 // No camera found
    }

    /**
     * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width The minimum desired width
     * @param height The minimum desired height
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    protected fun chooseOptimalSize(
        choices: ArrayList<Size>,
        width: Int,
        height: Int
    ): Size? {
        val minSize =
            Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE)
        val desiredSize = Size(width, height)

        // Collect the supported resolutions that are at least as big as the preview Surface
        var exactSizeFound = false
        val bigEnough: MutableList<Size> =
            ArrayList()
        val tooSmall: MutableList<Size> =
            ArrayList()
        for (option in choices) {
            if (option == desiredSize) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true
            }
            if (option.height >= minSize && option.width >= minSize) {
                bigEnough.add(option)
            } else {
                tooSmall.add(option)
            }
        }
        if (exactSizeFound) {
            return desiredSize
        }

        // Pick the smallest of those, assuming we found any
        return if (bigEnough.size > 0) {
            val chosenSize: Size = Collections.min(bigEnough, CompareSizesByArea())
            chosenSize
        } else {
            choices[0]
        }
    }

    /** Compares two `Size`s based on their areas.  */
    inner class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }

    private fun getRotation(): Int {
        val windowManager =
            context!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        when (val rotation = windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
            else -> Log.e(TAG, "Bad rotation value: $rotation")
        }

        val cameraInfo = Camera.CameraInfo()

        Camera.getCameraInfo(getIdForRequestedCamera(Camera.CameraInfo.CAMERA_FACING_BACK), cameraInfo)

        val angle: Int

        angle = if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (cameraInfo.orientation + mLastOrientation + degrees) % 360
        } else { // back-facing
            (mLastOrientation + cameraInfo.orientation - degrees + 360) % 360
        }

        // This corresponds to the rotation constants.
        return when (angle) {
            0 -> {
                angle/90 //9 o'clock
            }
            90 -> {
               if(isFrontCamera) angle/90 else angle/90 //12 o'clock
            }
            else -> {
                angle/90 //3 o'clock
            }
        }
    }

    private fun getIdForRequestedCamera(facing: Int): Int {
        val cameraInfo = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == facing) {
                return i
            }
        }
        return -1
    }


    private var mLastOrientation = 0
    private var orientationEventListener: OrientationEventListener? = null
    private fun setOrientationListener(isEnable: Boolean) {
        if (orientationEventListener == null) {
            orientationEventListener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    mLastOrientation = (orientation + 45) / 90 * 90
                }
            }
        }
        if (isEnable) {
            orientationEventListener!!.enable()
        } else {
            orientationEventListener!!.disable()
        }
    }
}