package app.pivo.android.prosdkdemo

import android.os.Bundle
import android.view.View
import app.pivo.android.prosdkdemo.camera.CameraController
import kotlinx.android.synthetic.main.fragment_camera_base.*

class Camera2Fragment : CameraBaseFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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