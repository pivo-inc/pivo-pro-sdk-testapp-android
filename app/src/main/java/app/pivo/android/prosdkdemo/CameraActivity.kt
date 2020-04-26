package app.pivo.android.prosdkdemo

import android.content.res.Configuration
import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdk.PivoSensitivity
import app.pivo.android.prosdk.tracking.FrameMetadata
import app.pivo.android.prosdk.util.ITrackingListener
import app.pivo.android.prosdkdemo.camera.*
import kotlinx.android.synthetic.main.activity_camera.*
import kotlin.math.min

class CameraActivity : AppCompatActivity(), ICameraCallback {
    private var tracking:Tracking = Tracking.NONE
    private var sensitivity:PivoSensitivity = PivoSensitivity.NORMAL
    private lateinit var cameraController: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)


        switch_camera_view.setOnClickListener {
            trackingStarted = false
            PivoProSdk.getInstance().stop()
            cameraController.switchCamera()
        }

        toggle_btn_tracking?.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked){
                when(checkedId){
                    R.id.none_tr ->{
                        tracking = Tracking.NONE
                    }
                    R.id.action_tr ->{
                        tracking = Tracking.ACTION
                    }
                    R.id.person_tr ->{
                        tracking = Tracking.PERSON
                    }
                    R.id.horse_tr ->{
                        tracking = Tracking.HORSE
                    }
                }
            }
            restart()
            updateUI()
        }

        toggle_btn_sensitivity?.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked){
                when(checkedId){
                    R.id.none_sen ->{
                        sensitivity = PivoSensitivity.NONE
                    }
                    R.id.slow_sen ->{
                        sensitivity = PivoSensitivity.SLOW
                    }
                    R.id.normal_sen ->{
                        sensitivity = PivoSensitivity.NORMAL
                    }
                    R.id.fast_sen ->{
                        sensitivity = PivoSensitivity.FAST
                    }
                }
                restart()
            }
        }
        //create [CameraController] object
        cameraController = CameraController(this, texture)

        //tracking layout
        tracking_graphic_overlay.setListener(actionSelectListener)
    }

    private fun restart(){
        trackingStarted = false

        if (tracking == Tracking.ACTION){
            region = null
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()

        cameraController.onResume()
    }

    override fun onPause() {
        cameraController.onPause()

        super.onPause()
    }

    private fun updateUI(){
        tracking_graphic_overlay.setTrackingMethod(tracking)

        val handler = Handler()
        handler.postDelayed({
            tracking_graphic_overlay.clear()
        }, 500)
    }

    override fun onCameraError() {
        finish()
    }

    override fun onCameraDisconnect() {
        finish()
    }

    private var trackingStarted = false
    private var frontCamera = false
    override fun onProcessingFrame(image: Image, width:Int, height:Int, orientation:Int, frontCamera:Boolean) {
        this.frontCamera = frontCamera

        if(!ViewManager.isOrientationLocked(this)){
            if (orientation == 1 || orientation == 3) {// portrait mode
                tracking_graphic_overlay.setCameraInfo(height, width, frontCamera)
            } else {// landscape mode
                tracking_graphic_overlay.setCameraInfo(width, height, frontCamera)
            }
        }else{// orientation locked(portrait)
            tracking_graphic_overlay.setCameraInfo(height, width, frontCamera)
        }

        //Create frame metadata
        val metadata = FrameMetadata.Builder()
            .setLayoutWidth(layoutWidth)
            .setLayoutHeight(layoutHeight)
            .setWidth(width)
            .setHeight(height)
            .setCameraFacing(frontCamera)
            .setOrientationLocked(ViewManager.isOrientationLocked(this))
            .setRotation(orientation)
            .build()

        when(tracking){//person
            Tracking.PERSON->{
                if (!trackingStarted){
                    PivoProSdk.getInstance().starPersonTracking(metadata, image, sensitivity , personTrackerListener)
                    trackingStarted = true
                }else{
                    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                }
            }
            Tracking.ACTION->{//action
                if (region!=null && !trackingStarted){
                    PivoProSdk.getInstance().startActionTracking(metadata, region, image, sensitivity, actionTrackerListener)
                    region = null
                    trackingStarted = true
                }else{
                    if (trackingStarted){
                        PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                    }else{
                        image.close()
                    }
                }
            }
            Tracking.HORSE->{
                if (!trackingStarted){
                    PivoProSdk.getInstance().startHorseTracking(metadata, image, sensitivity, actionTrackerListener)
                    region = null
                    trackingStarted = true
                }else {
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

    // action tracking drawing region
    private var region:Rect?=null
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

    private val actionTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(x: Int, y: Int, width: Int, height: Int, frameWidth: Int, frameHeight: Int) {
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

    private val personTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(x: Int, y: Int, width: Int, height: Int, frameWidth: Int, frameHeight: Int) {
            // clear graphic overlay
            tracking_graphic_overlay.clear()
            // being tracked object
            val rect = Rect(x, y, x + width, y + height)

            // create an instance of ActionGraphic and add view to parent tracking layout
            val graphic = ActionGraphic(tracking_graphic_overlay, rect)
            tracking_graphic_overlay.add(graphic)
            tracking_graphic_overlay.postInvalidate()
        }

        override fun onTracking(rect: Rect?) {
            tracking_graphic_overlay.clear()

            if (rect!=null)
            {
                val graphic = ActionGraphic(tracking_graphic_overlay, rect)
                tracking_graphic_overlay.add(graphic)
                tracking_graphic_overlay.postInvalidate()
            }else{
                Log.e("Camera", "update onTracking")
            }

        }

        override fun onClear() {}
    }
    /**
     * This function is called to match aspect ratio
     */
    private var layoutWidth:Int = 0
    private var layoutHeight:Int = 0
    override fun onCameraOpened() {
        runOnUiThread(Runnable {
            layoutWidth = min(texture.width, texture.height)
            layoutHeight = layoutWidth / 3 * 4
            val previewLayout:View = findViewById(R.id.tracking_graphic_overlay)

            val params = previewLayout.layoutParams
            if (resources.configuration.orientation === Configuration.ORIENTATION_PORTRAIT) {
                params.width = layoutWidth
                params.height = layoutHeight
            } else {
                params.width = layoutHeight
                params.height = layoutWidth
            }
            previewLayout.layoutParams = params
        })
    }
}
