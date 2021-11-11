package app.pivo.android.prosdkdemo

import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.usb.UsbEndpoint
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
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
import java.lang.Exception
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.charset.Charset
//UDP 관련
import java.net.DatagramPacket
import java.net.DatagramSocket

open class CameraBaseFragment : Fragment(), ICameraCallback {

    //val: 상수, var: 변수
    //소켓 통신을 위한 변수
    val ip: String = "192.168.0.93"  //192.168.0.93(연구실 노트북 ip주소) (서버 주소)
    val port: Int = 9999 //port 번호(정수여야 한다) (사용할 통신 포트)
    var client: DatagramSocket? = null //클라이언트 소켓
    var serverAddr: InetAddress? = null //retrieve the servername

    var tracking: Tracking = Tracking.NONE
    var sensitivity: PivoSensitivity = PivoSensitivity.NORMAL
    lateinit var cameraController: CameraController

    //이 스크립트 처음 시작, unity의 void Start()같은 부분
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_base, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //소켓 생성
        try {
            //클라이언트 소켓 종료 + 시작, UDP 소켓 생성
            client?.close()
            serverAddr = InetAddress.getByName(ip)
            client = DatagramSocket(port)
            var welcome_message = "Hello! I'm android, client."
            var packet = DatagramPacket(welcome_message.toByteArray(), welcome_message.length, serverAddr, port)
            client?.send(packet) //서버로 데이터 보내기, ?: null일 수 있음을 의미

        } catch (e: Exception){
            Log.d("Exception","socket connect exception start!!!")
        }

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

            //바운딩 박스 백분율 계산
            val x1: Float = x.toFloat() / 960
            val x2: Float = (x + width).toFloat() / 960
            val y1: Float = y.toFloat() / 720
            val y2: Float = (y + height).toFloat() / 720

            //바운딩 박스 영역 출력(0,0) -> (960,720)
            Log.d("tracking", "box: " + x1 + " " + y1 + " " + x2 + " " + y2)

            //소켓 데이터 송신하기
            var sendT:ByteArray = ByteArray(16) //크기 16(4바이트 * 4개)
            var x1_int = x1.toInt()
            var x2_int = x2.toInt()
            var y1_int = y1.toInt()
            var y2_int = y2.toInt()
            sendT[0] = (x1_int shr 24).toByte() //shr: >>
            sendT[1] = (x1_int shr 16).toByte()
            sendT[2] = (x1_int shr 8).toByte()
            sendT[3] = (x1_int).toByte()
            sendT[4] = (x2_int shr 24).toByte()
            sendT[5] = (x2_int shr 16).toByte()
            sendT[6] = (x2_int shr 8).toByte()
            sendT[7] = (x2_int).toByte()
            sendT[8] = (y1_int shr 24).toByte()
            sendT[9] = (y1_int shr 16).toByte()
            sendT[10] = (y1_int shr 8).toByte()
            sendT[11] = (y1_int).toByte()
            sendT[12] = (y2_int shr 24).toByte()
            sendT[13] = (y2_int shr 16).toByte()
            sendT[14] = (y2_int shr 8).toByte()
            sendT[15] = (y2_int).toByte()

            var packet = DatagramPacket(sendT, sendT.size, serverAddr, port)
            client?.send(packet) //서버로 데이터 보내기, ?: null일 수 있음을 의미

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

    //데이터 보내는 쓰레드 클래스
    class SendData: Thread(){
        override fun run(){
            try {
                //UDP 통신용 소켓 생성
                var socket: DatagramSocket = DatagramSocket()
                //서버 주소 변수

            } catch (e: Exception) {

            }
        }
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