package app.pivo.android.prosdkdemo

import android.content.res.Configuration
import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.pivo.android.prosdk.ImageUtils
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdk.PivoSensitivity
import app.pivo.android.prosdk.tracking.FrameMetadata
import app.pivo.android.prosdk.util.ITrackingListener
import app.pivo.android.prosdkdemo.camera.*
import kotlinx.android.synthetic.main.fragment_camera_base.*
import kotlin.math.min

//소켓 통신 위한 것
import java.io.*;
import java.net.Socket

open class CameraBaseFragment : Fragment(), ICameraCallback {

    //소켓 통신
    val ip = "192.168.0.93" // 192.168.0.0
    val port = 9999 // 여기에 port를 입력해주세요

    val client = Socket(ip, port)
    val output = PrintWriter(client.getOutputStream(), true)
    //val input = BufferedReader(InputStreamReader(client.inputStream))

    //

    var tracking: Tracking = Tracking.NONE
    var sensitivity: PivoSensitivity = PivoSensitivity.NORMAL
    lateinit var cameraController: CameraController

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_base, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switch_camera_view.setOnClickListener {
            switchCamera()
        }

        toggle_btn_tracking?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.none_tr -> {
                        tracking = Tracking.NONE
                    }
                    R.id.action_tr -> {
                        tracking = Tracking.ACTION
                    }
                    R.id.person_tr -> {
                        tracking = Tracking.PERSON
                    }
                    R.id.horse_tr -> {
                        tracking = Tracking.HORSE
                    }
                }
            }
            restart()
            updateUI()
        }

        toggle_btn_sensitivity?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.none_sen -> {
                        sensitivity = PivoSensitivity.NONE
                    }
                    R.id.slow_sen -> {
                        sensitivity = PivoSensitivity.SLOW
                    }
                    R.id.normal_sen -> {
                        sensitivity = PivoSensitivity.NORMAL
                    }
                    R.id.fast_sen -> {
                        sensitivity = PivoSensitivity.FAST
                    }
                }
                restart()
            }
        }

        //tracking layout
        tracking_graphic_overlay.setListener(actionSelectListener)
    }

    open fun switchCamera() {
        trackingStarted = false
        PivoProSdk.getInstance().stop()
    }

    fun restart() {
        trackingStarted = false

        if (tracking == Tracking.ACTION) {
            region = null
            updateUI()
        }
    }


    private fun updateUI() {
        if (tracking_graphic_overlay == null) return
        tracking_graphic_overlay.setTrackingMethod(tracking)

        val handler = Handler()
        handler.postDelayed({
            if (tracking_graphic_overlay != null) tracking_graphic_overlay.clear()
        }, 500)
    }

    override fun onCameraError() {
        //finish()
    }

    override fun onCameraDisconnect() {
        //finish()
    }

    private var trackingStarted = false
    private var frontCamera = false
    override fun onProcessingFrame(
        image: Image,
        width: Int,
        height: Int,
        orientation: Int,
        frontCamera: Boolean
    ) {
        this.frontCamera = frontCamera

        Log.e(
            "TTT",
            "orientation: $orientation locked: ${ViewManager.isOrientationLocked(requireContext())}"
        )

        if (!ViewManager.isOrientationLocked(requireContext())) {
            if (orientation == 1 || orientation == 3) {// portrait mode
                tracking_graphic_overlay.setCameraInfo(height, width, frontCamera)
            } else {// landscape mode
                tracking_graphic_overlay.setCameraInfo(width, height, frontCamera)
            }
        } else {// orientation locked(portrait)
            tracking_graphic_overlay.setCameraInfo(height, width, frontCamera)
        }
        //Log.d("TTT","바운딩 박스: " + height + " " + width); //박스 그리는 ..


        //Create frame metadata
        val metadata = FrameMetadata.Builder()
            .setLayoutWidth(layoutWidth)
            .setLayoutHeight(layoutHeight)
            .setWidth(width)
            .setHeight(height)
            .setCameraFacing(frontCamera)
            .setOrientationLocked(ViewManager.isOrientationLocked(requireActivity()))
            .setRotation(orientation)
            .build()

        when (tracking) {//person
            Tracking.PERSON -> {
                if (!trackingStarted) {
                    PivoProSdk.getInstance()
                        .starPersonTracking(metadata, image, sensitivity, aiTrackerListener)
                    trackingStarted = true
                } else {
                    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                }
            }
            Tracking.ACTION -> {//action
                if (region != null && !trackingStarted) {
                    PivoProSdk.getInstance().startActionTracking(
                        metadata,
                        region,
                        image,
                        sensitivity,
                        actionTrackerListener
                    )
                    region = null
                    trackingStarted = true
                } else {
                    if (trackingStarted) {
                        PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                    } else {
                        image.close()
                    }
                }
            }
            Tracking.HORSE -> {
                if (!trackingStarted) {
                    PivoProSdk.getInstance()
                        .startHorseTracking(metadata, image, sensitivity, aiTrackerListener)
                    region = null
                    trackingStarted = true
                } else {
                    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                }
            }
            else -> {
                image.close()
                trackingStarted = false
                region = null
            }
        }
    }

    override fun onProcessingFrame(
        byteArray: ByteArray,
        width: Int,
        height: Int,
        orientation: Int,
        frontCamera: Boolean
    ) {
        this.frontCamera = frontCamera

        Log.e(
            "TTT",
            "orientation: $orientation locked: ${ViewManager.isOrientationLocked(requireContext())}"
        )

        if (!ViewManager.isOrientationLocked(requireContext())) {
            if (orientation == 1 || orientation == 3) {// portrait mode
                tracking_graphic_overlay.setCameraInfo(height, width, frontCamera)
            } else {// landscape mode
                tracking_graphic_overlay.setCameraInfo(width, height, frontCamera)
            }
        } else {// orientation locked(portrait)
            tracking_graphic_overlay.setCameraInfo(height, width, frontCamera)
        }

        //Create frame metadata
        val metadata = FrameMetadata.Builder()
            .setLayoutWidth(layoutWidth)
            .setLayoutHeight(layoutHeight)
            .setWidth(width)
            .setHeight(height)
            .setCameraFacing(frontCamera)
            .setOrientationLocked(ViewManager.isOrientationLocked(requireActivity()))
            .setRotation(orientation)
            .build()

        when (tracking) {//person
            Tracking.PERSON -> {
                if (!trackingStarted) {
                    PivoProSdk.getInstance()
                        .starPersonTracking(metadata, byteArray, sensitivity, aiTrackerListener)
                    trackingStarted = true
                } else {
                    PivoProSdk.getInstance().updateTrackingFrame(byteArray, metadata)
                }
            }
            Tracking.ACTION -> {//action
                if (region != null && !trackingStarted) {
                    PivoProSdk.getInstance().startActionTracking(
                        metadata,
                        region,
                        byteArray,
                        sensitivity,
                        actionTrackerListener
                    )
                    region = null
                    trackingStarted = true
                } else {
                    if (trackingStarted) {
                        PivoProSdk.getInstance().updateTrackingFrame(byteArray, metadata)
                    }
                }
            }
            Tracking.HORSE -> {
                if (!trackingStarted) {
                    PivoProSdk.getInstance()
                        .startHorseTracking(metadata, byteArray, sensitivity, aiTrackerListener)
                    region = null
                    trackingStarted = true
                } else {
                    PivoProSdk.getInstance().updateTrackingFrame(byteArray, metadata)
                }
            }
            else -> {
                trackingStarted = false
                region = null
            }
        }
    }


    // action tracking drawing region
    private var region: Rect? = null

    // action drawing callback
    private val actionSelectListener: IActionSelector = object : IActionSelector {
        override fun onReset() {
            //release tracking
        }

        override fun onSelect(objRegion: Rect?) {
            // reset tracking area selector variable
            tracking_graphic_overlay.reset()
            // set tracking region
            trackingStarted = false
            region = objRegion
        }
    }

    //액션, 오브젝트 트래킹
    private val actionTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            frameWidth: Int,
            frameHeight: Int
        ) {
            // clear graphic overlay
            tracking_graphic_overlay.clear()
            // being tracked object
            val rect = Rect(x, y, x + width, y + height)


            // create an instance of ActionGraphic and add view to parent tracking layout
            val graphic = ActionGraphic(tracking_graphic_overlay, rect)
            tracking_graphic_overlay.add(graphic)
            tracking_graphic_overlay.postInvalidate()
        }

        override fun onClear() {}
    }

    //사람, 말 트래킹
    private val aiTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            frameWidth: Int,
            frameHeight: Int
        ) {
            // clear graphic overlay
            tracking_graphic_overlay.clear()
            // being tracked object
            val rect = Rect(x, y, x + width, y + height)
            //바운딩 박스 영역 출력(0,0) -> (960,720)
            Log.d("tracking", "box: " + x + " " + y + " " + (x + width) + " " + (y + height))

            var data :String = (" " + x + " " + y + " " + (x + width) + " " + (y + height))
            output.write(data) //출력 스트림에 데이터 넣기
            output.flush(); //출력


            // create an instance of ActionGraphic and add view to parent tracking layout
            val graphic = ActionGraphic(tracking_graphic_overlay, rect)
            tracking_graphic_overlay.add(graphic)
            tracking_graphic_overlay.postInvalidate()
        }

        override fun onTracking(rect: Rect?) {
            tracking_graphic_overlay.clear()

            if (rect != null) {
                val graphic = ActionGraphic(tracking_graphic_overlay, rect)
                tracking_graphic_overlay.add(graphic)
                tracking_graphic_overlay.postInvalidate()
            } else {
                Log.e("Camera", "update onTracking")
            }
        }

        override fun onClear() {}
    }

    /**
     * This function is called to match aspect ratio
     */
    private var layoutWidth: Int = 0
    private var layoutHeight: Int = 0
    override fun onCameraOpened() {
        requireActivity().runOnUiThread {
            layoutWidth = min(texture.width, texture.height)
            layoutHeight = layoutWidth / 3 * 4
            val previewLayout: View = tracking_graphic_overlay

            val params = previewLayout.layoutParams
            if (resources.configuration.orientation === Configuration.ORIENTATION_PORTRAIT) {
                params.width = layoutWidth
                params.height = layoutHeight
            } else {
                params.width = layoutHeight
                params.height = layoutWidth
            }
            previewLayout.layoutParams = params
        }
    }
}