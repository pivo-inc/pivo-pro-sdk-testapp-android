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
        val fragment = Camera2Fragment()
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment as Fragment).commit()
    }
}
