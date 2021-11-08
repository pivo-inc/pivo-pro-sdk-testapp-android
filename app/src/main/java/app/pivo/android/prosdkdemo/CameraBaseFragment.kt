package app.pivo.android.prosdkdemo

import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.usb.UsbEndpoint
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Xml
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.charset.Charset

open class CameraBaseFragment : Fragment(), ICameraCallback {

    //소켓 통신을 위한 변수
    val ip: String = "192.168.0.93"  //192.168.0.93(연구실 노트북 ip주소)
    val port: Int = 3000 //port 번호(정수여야 한다)

    //소켓 통신에 보낼 데이터 형태
    var client: Socket? = null //클라이언트 소켓

    var tracking: Tracking = Tracking.NONE
    var sensitivity: PivoSensitivity = PivoSensitivity.NORMAL
    lateinit var cameraController: CameraController

    //이 스크립트 처음 시작, unity의 void Start()같은 부분
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //클라이언트 소켓 시작, UDP 소켓 생성
        client = Socket(InetAddress.getByName(ip), port)

        //Ubuntu ros master 와 NW 통신 개시
        val addr: SocketAddress = InetSocketAddress(ip, port) //서버에 연결 요청
        client!!.connect(addr)

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
            val rect = Rect(x, y, x + width, y + height) //x: 열, y: 행

            //바운딩 박스 그대로 송신 -> 백분율 계산은 ros 노드에서 하기
            val left_x: Int = x
            val right_x: Int = (x + width)
            val left_y: Int = y
            val right_y: Int = (y + height)

            //바운딩 박스 영역 출력(0,0) -> (960,720)
            Log.d("tracking", "box: " + x + " " + y + " " + (x + width) + " " + (y + height))

            //소켓 데이터 송신하기
            //val bufSnd: ByteArray =

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