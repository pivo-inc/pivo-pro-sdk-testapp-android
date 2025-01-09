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
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdk.PivoSensitivity
import app.pivo.android.prosdk.tracking.FrameMetadata
import app.pivo.android.prosdk.util.ITrackingListener
import app.pivo.android.prosdkdemo.camera.ActionGraphic
import app.pivo.android.prosdkdemo.camera.CameraController
import app.pivo.android.prosdkdemo.camera.IActionSelector
import app.pivo.android.prosdkdemo.camera.ICameraCallback
import app.pivo.android.prosdkdemo.camera.Tracking
import app.pivo.android.prosdkdemo.camera.ViewManager
import app.pivo.android.prosdkdemo.databinding.FragmentCameraBaseBinding
import kotlin.math.min

open class CameraBaseFragment : Fragment(), ICameraCallback {

    private var tracking: Tracking = Tracking.NONE
    private var sensitivity: PivoSensitivity = PivoSensitivity.NORMAL
    lateinit var cameraController: CameraController
    
    lateinit var binding: FragmentCameraBaseBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCameraBaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchCameraView.setOnClickListener {
            switchCamera()
        }

        binding.toggleBtnTracking.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked){
                when(checkedId){
                    R.id.none_tr ->{
                        tracking = Tracking.NONE
                    }
                    R.id.face_tr ->{
                        tracking = Tracking.FACE
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

        binding.toggleBtnSensitivity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked){
                when(checkedId){
                    R.id.none_sen ->{
                        sensitivity = PivoSensitivity.NONE
                    }
                    R.id.slower_sen ->{
                        sensitivity = PivoSensitivity.SLOWER
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

        //tracking layout
        binding.trackingGraphicOverlay.setListener(actionSelectListener)
    }

    open fun switchCamera(){
        trackingStarted = false
        PivoProSdk.getInstance().stop()
    }

    fun restart(){
        trackingStarted = false

        if (tracking == Tracking.ACTION){
            region = null
            updateUI()
        }
    }


    private fun updateUI(){
        binding.trackingGraphicOverlay.setTrackingMethod(tracking)

        val handler = Handler()
        handler.postDelayed({
            binding.trackingGraphicOverlay.clear()
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
    override fun onProcessingFrame(image: Image, width:Int, height:Int, orientation:Int, frontCamera:Boolean) {
        this.frontCamera = frontCamera
        if(!ViewManager.isOrientationLocked(requireContext())){
            if (orientation == 1 || orientation == 3) {// portrait mode
                binding.trackingGraphicOverlay.setCameraInfo(height, width, frontCamera)
            } else {// landscape mode
                binding.trackingGraphicOverlay.setCameraInfo(width, height, frontCamera)
            }
        }else{// orientation locked(portrait)
            binding.trackingGraphicOverlay.setCameraInfo(height, width, frontCamera)
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

        when(tracking){
            Tracking.FACE->{
                PivoProSdk.getInstance().starFaceTracking(metadata, image, sensitivity, aiTrackerListener)
                region = null
                trackingStarted = true
            }
            Tracking.PERSON->{
                if (!trackingStarted){
                    PivoProSdk.getInstance().starPersonTracking(metadata, image, sensitivity , aiTrackerListener)
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
                    PivoProSdk.getInstance().startHorseTracking(metadata, image, sensitivity, aiTrackerListener)
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

    override fun onProcessingFrame(byteArray: ByteArray, width:Int, height:Int, orientation:Int, frontCamera:Boolean){
        this.frontCamera = frontCamera
        if(!ViewManager.isOrientationLocked(requireContext())){
            if (orientation == 1 || orientation == 3) {// portrait mode
                binding.trackingGraphicOverlay.setCameraInfo(height, width, frontCamera)
            } else {// landscape mode
                binding.trackingGraphicOverlay.setCameraInfo(width, height, frontCamera)
            }
        }else{// orientation locked(portrait)
            binding.trackingGraphicOverlay.setCameraInfo(height, width, frontCamera)
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

        when(tracking){
            Tracking.FACE->{
                PivoProSdk.getInstance().starFaceTracking(metadata, byteArray, sensitivity, aiTrackerListener)
                region = null
                trackingStarted = true
            }
            Tracking.PERSON->{
                if (!trackingStarted){
                    try {
                        PivoProSdk.getInstance().starPersonTracking(metadata, byteArray, sensitivity , aiTrackerListener)
                    } catch (e: Exception) {
                        Log.e("aa", "${e.message}")
                    }
                    trackingStarted = true
                }else{
                    PivoProSdk.getInstance().updateTrackingFrame(byteArray, metadata)
                }
            }
            Tracking.ACTION->{//action
                if (region!=null && !trackingStarted){
                    PivoProSdk.getInstance().startActionTracking(metadata, region, byteArray, sensitivity, actionTrackerListener)
                    region = null
                    trackingStarted = true
                }else{
                    if (trackingStarted){
                        PivoProSdk.getInstance().updateTrackingFrame(byteArray, metadata)
                    }
                }
            }
            Tracking.HORSE->{
                if (!trackingStarted){
                    PivoProSdk.getInstance().startHorseTracking(metadata, byteArray, sensitivity, aiTrackerListener)
                    region = null
                    trackingStarted = true
                }else {
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
    private var region: Rect?=null
    // action drawing callback
    private val actionSelectListener: IActionSelector = object : IActionSelector {
        override fun onReset() {
            //release tracking
        }

        override fun onSelect(objRegion: Rect?) {
            // reset tracking area selector variable
            binding.trackingGraphicOverlay.reset()
            // set tracking region
            trackingStarted = false
            region = objRegion
        }
    }

    private val actionTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(x: Int, y: Int, width: Int, height: Int, frameWidth: Int, frameHeight: Int) {
            // clear graphic overlay
            binding.trackingGraphicOverlay.clear()
            // being tracked object
            val rect = Rect(x, y, x + width, y + height)

            // create an instance of ActionGraphic and add view to parent tracking layout
            val graphic = ActionGraphic(binding.trackingGraphicOverlay, rect)
            binding.trackingGraphicOverlay.add(graphic)
            binding.trackingGraphicOverlay.postInvalidate()
        }

        override fun onClear() {}
    }

    private val aiTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(x: Int, y: Int, width: Int, height: Int, frameWidth: Int, frameHeight: Int) {
            // clear graphic overlay
            binding.trackingGraphicOverlay.clear()
            // being tracked object
            val rect = Rect(x, y, x + width, y + height)

            // create an instance of ActionGraphic and add view to parent tracking layout
            val graphic = ActionGraphic(binding.trackingGraphicOverlay, rect)
            binding.trackingGraphicOverlay.add(graphic)
            binding.trackingGraphicOverlay.postInvalidate()
        }

        override fun onClear() {}
    }
    /**
     * This function is called to match aspect ratio
     */
    private var layoutWidth:Int = 0
    private var layoutHeight:Int = 0
    override fun onCameraOpened() {
        requireActivity().runOnUiThread {
            layoutWidth = min(binding.texture.width, binding.texture.height)
            layoutHeight = layoutWidth / 3 * 4
            val previewLayout:View = binding.trackingGraphicOverlay

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