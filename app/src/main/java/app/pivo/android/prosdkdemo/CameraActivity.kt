package app.pivo.android.prosdkdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        setFragment()
    }

    private fun setFragment() {
        val cameraApi = intent.getIntExtra(PivoControllerActivity.CAMERA_TYPE_MSG_CODE, 1)
        val fragment = if (cameraApi == 2) {
            Camera2Fragment()
        } else {
            LegacyCameraFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment as Fragment).commit()
    }
}
