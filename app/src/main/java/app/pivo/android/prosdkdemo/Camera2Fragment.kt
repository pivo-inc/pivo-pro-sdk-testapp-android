package app.pivo.android.prosdkdemo

import android.os.Bundle
import android.util.Log
import android.view.View
import app.pivo.android.prosdk.PivoAuthentication
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdkdemo.camera.CameraController
import kotlinx.android.synthetic.main.fragment_camera_base.*

class Camera2Fragment : CameraBaseFragment() {

    val TAG = this.javaClass.simpleName

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.e(TAG, "onViewCreated")

        //create [CameraController] object
        cameraController = CameraController(requireActivity(), this, texture)
    }

    override fun onResume() {
        super.onResume()

        cameraController.onResume()
    }

    override fun onPause() {
        cameraController.onPause()

        super.onPause()
    }

    override fun switchCamera() {
        super.switchCamera()
        cameraController.switchCamera()
    }
}