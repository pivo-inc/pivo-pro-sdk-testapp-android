package app.pivo.android.prosdkdemo.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Long
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.min

/**
 * Created by murodjon on 2020/04/10
 */
class CameraController(
    private var context: Context,
    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private var textureView: AutoFitTextureView) {

    private var listener:ICameraCallback? = null

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * A [CameraCharacteristics]
     */
    private var characteristics:CameraCharacteristics?=null

    /**
     * A [CameraCharacteristics.LENS_FACING]
     */
    private var cameraDirection:Int?=CameraCharacteristics.LENS_FACING_FRONT

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * The [android.util.Size] of camera preview
     */
    private var frameSize: Size?=null

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(_cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = _cameraDevice
            createCameraPreviewSession()
            listener?.onCameraOpened()
        }

        override fun onDisconnected(_cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            _cameraDevice.close()
            cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            listener?.onCameraError()
        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    private var imageReader: ImageReader? = null

    /**
     * An [ImageReader] that handles frames for tracking algorithms
     */
    private var frameProcessingReader:ImageReader? = null

    /**
     * This is the output file for our picture.
     */
    private lateinit var file: File

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        backgroundHandler?.post(
            ImageSaver(
                it.acquireNextImage(),
                file
            )
        )
    }

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when
     * a frame is ready to process
     */
    private val onFrameAvailableListener = ImageReader.OnImageAvailableListener {
        val image = it.acquireLatestImage() ?: return@OnImageAvailableListener
        listener?.onProcessingFrame(image, frameSize!!.width, frameSize!!.height, getTrackingFrameRotation()/90, isFrontCamera())
    }

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */
    private lateinit var previewRequest: CaptureRequest

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .captureCallback
     */
    private var state =
        STATE_PREVIEW

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var flashSupported = false

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            when (state) {
                STATE_PREVIEW -> Unit // Do nothing when the camera preview is working normally.
                STATE_WAITING_LOCK -> capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state =
                            STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state =
                            STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        private fun capturePicture(result: CaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null) {
                captureStillPicture()
            } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                // CONTROL_AE_STATE can be null on some devices
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    state =
                        STATE_PICTURE_TAKEN
                    captureStillPicture()
                } else {
                    runPrecaptureSequence()
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            process(result)
        }
    }

    fun onResume() {
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
        setOrientationListener(true)
    }

    fun onPause() {
        closeCamera()
        stopBackgroundThread()
        setOrientationListener(false)
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val lensFacing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                if (lensFacing != null &&
                    lensFacing != cameraDirection) {
                    continue
                }

                val map = characteristics?.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                // For still image captures, we use the largest available size.
                val largest = Collections.max(listOf(*map.getOutputSizes(ImageFormat.JPEG)), CompareSizesByArea())
                imageReader = ImageReader.newInstance(largest.width, largest.height,
                    ImageFormat.JPEG, /*maxImages*/ 2).apply {
                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
                }

                frameSize = getFrameSize()
                frameProcessingReader = if (frameSize!=null){
                    ImageReader.newInstance(frameSize!!.width, frameSize!!.height, ImageFormat.YUV_420_888, 3)
                }else{
                    ImageReader.newInstance(largest.width, largest.height, ImageFormat.YUV_420_888, 3)
                }
                frameProcessingReader?.setOnImageAvailableListener(onFrameAvailableListener, backgroundHandler)

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = (context as Activity).windowManager.defaultDisplay.rotation

                sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                val swappedDimensions = areDimensionsSwapped(displayRotation)

                val displaySize = Point()
                (context as Activity).windowManager.defaultDisplay.getSize(displaySize)
                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height
                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize =
                    chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight,
                        maxPreviewWidth, maxPreviewHeight,
                        largest)

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView.setAspectRatio(previewSize.height, previewSize.width)
                }

                // Check if the flash is supported.
                flashSupported = characteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                this.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.cameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            return
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    fun isFrontCamera():Boolean{
        return cameraDirection == CameraCharacteristics.LENS_FACING_FRONT
    }

    fun switchCamera(){
        if(characteristics!=null){
            cameraDirection = if(characteristics?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                CameraCharacteristics.LENS_FACING_FRONT
            else CameraCharacteristics.LENS_FACING_BACK
            // close camera before opening
            closeCamera()
            if (textureView.isAvailable) {
                //open camera if the textureView is available
                openCamera(textureView.width, textureView.height)
            } else {
                textureView.surfaceTextureListener = surfaceTextureListener
            }
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder.addTarget(surface)
            frameProcessingReader?.surface?.let { previewRequestBuilder.addTarget(it) }

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(
                listOf(surface, imageReader?.surface, frameProcessingReader?.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            // Flash is automatically enabled when necessary.
                            setAutoFlash(previewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder.build()
                            captureSession?.setRepeatingRequest(previewRequest,
                                captureCallback, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
//                        showToast("Failed")
                    }
                }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = (context as Activity).windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    fun takePicture(){
        lockFocus()
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START)
            // Tell #captureCallback to wait for the lock.
            state =
                STATE_WAITING_LOCK
            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.captureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            // Tell #captureCallback to wait for the precapture sequence to be set.
            state =
                STATE_WAITING_PRECAPTURE
            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.captureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        try {
            if ( cameraDevice == null) return
            val rotation = (context as Activity).windowManager.defaultDisplay.rotation

            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                imageReader?.surface?.let { addTarget(it) }

                // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
                // We have to take that into account and rotate JPEG properly.
                // For devices with orientation of 90, we return our mapping from ORIENTATIONS.
                // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
                set(
                    CaptureRequest.JPEG_ORIENTATION,
                    (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360)

                // Use the same AE and AF modes as the preview.
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }?.also { setAutoFlash(it) }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult
                ) {
                    //activity.showToast("Saved: $file")
                    Log.d(TAG, file.toString())
                    unlockFocus()
                }
            }

            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                captureBuilder?.build()?.let { capture(it, captureCallback, null) }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            setAutoFlash(previewRequestBuilder)
            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                backgroundHandler)
            // After this, the camera will go back to the normal state of preview.
            state =
                STATE_PREVIEW
            captureSession?.setRepeatingRequest(previewRequest, captureCallback,
                backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (flashSupported) {
            requestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    companion object {

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Tag for the [Log].
         */
        private val TAG = "Camera2BasicFragment"

        /**
         * Camera state: Showing camera preview.
         */
        private val STATE_PREVIEW = 0

        /**
         * Camera state: Waiting for the focus to be locked.
         */
        private val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        private val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        private val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken.
         */
        private val STATE_PICTURE_TAKEN = 4

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private val MAX_PREVIEW_HEIGHT = 1080

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as
         * the respective max size, and whose aspect ratio matches with the specified value. If such
         * size doesn't exist, choose the largest one that is at most as large as the respective max
         * size, and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended
         *                          output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        @JvmStatic private fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                    option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size > 0) {
                return Collections.min(bigEnough,
                    CompareSizesByArea()
                )
            } else if (notBigEnough.size > 0) {
                return Collections.max(notBigEnough,
                    CompareSizesByArea()
                )
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                return choices[0]
            }
        }
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {

        // We cast here to ensure the multiplications won't overflow
        override fun compare(lhs: Size, rhs: Size) =
            Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)

    }

    /**
     * Saves a JPEG [Image] into the specified [File].
     */
    internal class ImageSaver(
        /**
         * The JPEG image
         */
        private val image: Image,

        /**
         * The file we save the image into.
         */
        private val file: File
    ) : Runnable {

        override fun run() {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var output: FileOutputStream? = null
            try {
                output = FileOutputStream(file).apply {
                    write(bytes)
                }
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
            } finally {
                image.close()
                output?.let {
                    try {
                        it.close()
                    } catch (e: IOException) {
                        Log.e(TAG, e.toString())
                    }
                }
            }
        }

        companion object {
            /**
             * Tag for the [Log].
             */
            private val TAG = "ImageSaver"
        }
    }

    private fun getFrameSize(): Size? {
        return if (getAllSizes() != null) {
            getFrameSize(getAllSizes(), PHOTO_RATIO)
        } else null
    }

    private fun getAllSizes(): Array<Size>? {
        val streamConfigurationMap: StreamConfigurationMap? =
            characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        return streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)
    }


    private val PHOTO_RATIO = 4.0 / 3.0
    private val VIDEO_RATIO = 16.0 / 9.0

    /**
     * Added by Murodjon
     * This function is used to select frame size for frame processing according to mode
     * @param sizes
     * @param targetRatio
     * @return appropriate minimum size for frame processing [android.util.Size]
     */
    private fun getFrameSize(
        sizes: Array<Size>?,
        targetRatio: Double
    ): Size? {
        val ASPECT_TOLERANCE = 0.001
        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE
        val display =
            (context as Activity).windowManager.defaultDisplay
        val displaySize = Point()
        display.getSize(displaySize)
        val targetHeight = min(displaySize.y, displaySize.x)

        // Try to find an size match aspect ratio and size
        for (size in sizes!!) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue
            if (abs(size.height - targetHeight) < minDiff && size.width < 1280 && size.width >= 300 && size.width != 800 && (targetRatio == PHOTO_RATIO || targetRatio == VIDEO_RATIO)
            ) {
                optimalSize = size
                minDiff = abs(size.height - targetHeight).toDouble()
            }
        }

        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.e(TAG, "No preview size match the aspect ratio")
            minDiff = Double.MAX_VALUE
            for (size in sizes) {
                if (abs(size.height - targetHeight) < minDiff && (size.height >= 300 && size.width < 1280 && targetRatio == PHOTO_RATIO || targetRatio == VIDEO_RATIO)) {
                    optimalSize = size
                    minDiff = abs(size.height - targetHeight).toDouble()
                }
            }
        }
        return optimalSize
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

    /**
     * Returns required orientation angle that the jpeg picture needs to be rotated to be displayed upright.
     */
    private fun getTrackingFrameRotation(): Int {
        var degrees = mLastOrientation
        if (characteristics == null) return 0
        if (characteristics!!.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            degrees = -degrees
        }
        return (characteristics!!.get(CameraCharacteristics.SENSOR_ORIENTATION)!! + degrees + 360) % 360
    }

    init {
        listener = context as ICameraCallback
    }
}