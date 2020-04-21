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
import app.pivo.android.prosdk.tracking.FrameMetadata
import app.pivo.android.prosdk.util.ITrackingListener
import app.pivo.android.prosdkdemo.camera.*
import kotlinx.android.synthetic.main.activity_camera.*
import kotlin.math.min

class CameraActivity : AppCompatActivity(), ICameraCallback, View.OnClickListener {
    private var tracking:Tracking = Tracking.NONE
    private lateinit var cameraController: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        picture.setOnClickListener {
//            cameraController.takePicture()
        }

        switch_camera_view.setOnClickListener {
            personStarted = false
            actionStarted = false
            PivoProSdk.getInstance().stopTracking()
            cameraController.switchCamera()
        }

        btn_cntrl_tracking.setOnClickListener (this)

        btn_action_view.setOnClickListener(this)

        btn_off_view.setOnClickListener (this)

        btn_person_view.setOnClickListener (this)

        //create [CameraController] object
        cameraController = CameraController(this, texture)

        //tracking layout
        tracking_graphic_overlay.setListener(actionSelectListener)
    }

    private var listShown = false
    override fun onClick(view: View?) {
        when(view!!.id){
            R.id.btn_cntrl_tracking->{
                if (listShown){
                    closeSlidingMenu()
                }
                listShown = true
                ViewManager.slideShow(this, btn_off_view, btn_person_view, btn_action_view)
                return
            }
            R.id.btn_action_view->{
                tracking = Tracking.ACTION
                updateTracking()
            }
            R.id.btn_person_view->{
                tracking = Tracking.PERSON
                updateTracking()
            }
            R.id.btn_off_view->{
                tracking = Tracking.NONE
                updateTracking()
            }
        }
        if (listShown){
            closeSlidingMenu()
        }
        updateTrackingUI()
    }

    override fun onResume() {
        super.onResume()

        cameraController.onResume()
    }

    override fun onPause() {
        cameraController.onPause()

        super.onPause()
    }

    private fun updateTracking(){
        tracking_graphic_overlay.setTrackingMethod(tracking)

        val handler = Handler()
        handler.postDelayed(Runnable {
            tracking_graphic_overlay.clear()
        }, 500)
    }

    override fun onCameraError() {
        finish()
    }

    override fun onCameraDisconnect() {
        finish()
    }

    private var personStarted = false
    private var actionStarted = false
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
                actionStarted = false
                if (!personStarted){
                    PivoProSdk.getInstance().starPersonTracking(metadata, image, personTrackerListener)
                    personStarted = true
                }else{
                    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                }
            }
            Tracking.ACTION->{//action
                personStarted = false
                if (region!=null && !actionStarted){
                    PivoProSdk.getInstance().startActionTracking(metadata, region, image, actionTrackerListener)
                    region = null
                    actionStarted = true
                }else{
                    if (actionStarted){
                        PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
                    }else{
                        image.close()
                    }
                }
            }
            else -> {
                image.close()
                personStarted = false
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
            actionStarted = false
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

    private fun closeSlidingMenu() {
        ViewManager.slideHide(true, btn_off_view, btn_person_view, btn_action_view)
        listShown = false
    }

    private fun updateTrackingUI(){
        when(tracking){
//            Tracking.NONE->{
//                btn_cntrl_tracking.setImageResource(R.mipmap.ic_gps_on)
//
//                btn_action_view.setColorFilter(resources.getColor(R.color.transparent))
//                btn_person_view.setImageResource(R.mipmap.ic_body_off)
//                btn_off_view.setColorFilter(resources.getColor(R.color.pivo_color))
//            }
//            Tracking.PERSON->{
//                btn_cntrl_tracking.setImageResource(R.mipmap.ic_body_on)
//
//                btn_person_view.setImageResource(R.mipmap.ic_body_on)
//                btn_action_view.setColorFilter(resources.getColor(R.color.transparent))
//                btn_off_view.setColorFilter(resources.getColor(R.color.transparent))
//            }
//            Tracking.HORSE->{
//                btn_cntrl_tracking.setImageResource(R.mipmap.ic_horse_on)
//
//                btn_action_view.setColorFilter(resources.getColor(R.color.transparent))
//                btn_person_view.setImageResource(R.mipmap.ic_body_off)
//                btn_off_view.setColorFilter(resources.getColor(R.color.transparent))
//            }
//            Tracking.ACTION->{
//                btn_cntrl_tracking.setImageResource(R.mipmap.ic_object_tracking_inactive)
//
//                btn_person_view.setImageResource(R.mipmap.ic_body_off)
//                btn_action_view.setColorFilter(resources.getColor(R.color.pivo_color))
//                btn_off_view.setColorFilter(resources.getColor(R.color.transparent))
//            }
        }
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
