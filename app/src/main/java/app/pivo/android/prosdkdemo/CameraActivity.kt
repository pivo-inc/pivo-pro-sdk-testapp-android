package app.pivo.android.prosdkdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import app.pivo.android.sdkdemo.pro.LegacyCameraFragment

class CameraActivity : AppCompatActivity() {

    private var useCamera2API = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        setFragment()
    }

    private fun setFragment() {
        val fragment = if (useCamera2API) {
            Camera2Fragment()
        } else {
            LegacyCameraFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment as Fragment).commit()
    }
}
