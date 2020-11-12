package app.pivo.android.prosdkdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.functions.Consumer
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdk.events.PivoEvent
import app.pivo.android.prosdk.events.PivoEventBus
import app.pivo.android.prosdkdemo.R
import kotlinx.android.synthetic.main.activity_pivo_controller.*


class PivoControllerActivity : AppCompatActivity() {

    private val TAG = "PivoControllerActivity"

    companion object{
        val CAMERA_TYPE_MSG_CODE = "CAMERA_TYPE_MSG_CODE"
    }

    private var position=0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pivo_controller)

        //get Pivo supported speed list
        val speedList = PivoProSdk.getInstance().supportedSpeedsInSecondsPerRound.toMutableList()

        //show pivo version
        version_view.text = "Pivo Type: ${PivoProSdk.getInstance().version.pivoType}\n Pivo Version: ${PivoProSdk.getInstance().version.version}"

        //rotate continuously to left
        btn_left_con_turn.setOnClickListener { PivoProSdk.getInstance().turnLeftContinuously(speedList[position]) }

        //rotate to left
        btn_left_turn.setOnClickListener { PivoProSdk.getInstance().turnLeft(getAngle(), speedList[position]) }

        //rotate continuously to right
        btn_right_con_turn.setOnClickListener { PivoProSdk.getInstance().turnRightContinuously(speedList[position]) }

        //rotate to right
        btn_right_turn.setOnClickListener { PivoProSdk.getInstance().turnRight(getAngle(), speedList[position]) }

        //stop rotating the device
        btn_stop.setOnClickListener { PivoProSdk.getInstance().stop() }

        //change Pivo name
        btn_change_name.setOnClickListener {
            if (input_pivo_name.text.isNotEmpty()){
                PivoProSdk.getInstance().changeName(input_pivo_name.text.toString())
            }
        }

        btn_camera1.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(CAMERA_TYPE_MSG_CODE, 1)
            startActivity(intent)
        }

        btn_camera2.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(CAMERA_TYPE_MSG_CODE, 2)
            startActivity(intent)
        }

        //speed list view
        speed_list_view.adapter= ArrayAdapter<Int>(this, android.R.layout.simple_spinner_item, speedList)
        speed_list_view.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, itemPosition: Int, id: Long) {
                Log.e(TAG, "onSpeedChange: ${speedList[itemPosition]} save: ${save_speed_view.isChecked}")
                position = itemPosition
                PivoProSdk.getInstance().setSpeedBySecondsPerRound(speedList[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onStart() {
        super.onStart()
        //subscribe to connection failure event
        PivoEventBus.subscribe(
            PivoEventBus.CONNECTION_FAILURE,this, Consumer {
                if (it is PivoEvent.ConnectionFailure){
                    finish()
                }
            })
        //subscribe pivo remote controller event
        PivoEventBus.subscribe(
            PivoEventBus.REMOTE_CONTROLLER, this, Consumer {
            when(it){
                is PivoEvent.RCCamera->notification_view.text = "CAMERA state: ${if(it.state==0)"Press" else "Release"}"
                is PivoEvent.RCMode->notification_view.text = "MODE: ${if(it.state==0)"Press" else "Release"}"
                is PivoEvent.RCStop->notification_view.text = "STOP: ${if(it.state==0)"Press" else "Release"}"
                is PivoEvent.RCRightContinuous->notification_view.text = "RIGHT_CONTINUOUS: ${if(it.state==0)"Press" else "Release"}"
                is PivoEvent.RCLeftContinuous->notification_view.text = "LEFT_CONTINUOUS: ${if(it.state==0)"Press" else "Release"}"
                is PivoEvent.RCLeft->notification_view.text = "LEFT: ${if(it.state==0)"Press" else "Release"}"
                is PivoEvent.RCRight->notification_view.text = "RIGHT: ${if(it.state==0)"Press" else "Release"}"

                /**
                 * This below events're deprecated
                 *
                is PivoEvent.RCLeftPressed->notification_view.text = "LEFT_PRESSED"
                is PivoEvent.RCLeftRelease->notification_view.text = "LEFT_RELEASE"
                is PivoEvent.RCRightPressed->notification_view.text = "RIGHT_PRESSED"
                is PivoEvent.RCRightRelease->notification_view.text = "RIGHT_RELEASE"
                is PivoEvent.RCSpeedUpPressed->notification_view.text = "SPEED_UP_PRESSED: ${it.level}"
                is PivoEvent.RCSpeedDownPressed->notification_view.text = "SPEED_DOWN_PRESSED: ${it.level}"
                is PivoEvent.RCSpeedUpRelease->notification_view.text = "SPEED_UP_RELEASE: ${it.level}"
                is PivoEvent.RCSpeedDownRelease->notification_view.text = "SPEED_DOWN_RELEASE: ${it.level}"
                 */

                is PivoEvent.RCSpeed->notification_view.text = "SPEED: : ${if(it.state==0)"Press" else "Release"} speed: ${it.level}"
            }
        })
        //subscribe to name change event
        PivoEventBus.subscribe(
            PivoEventBus.NAME_CHANGED, this, Consumer {
            if (it is PivoEvent.NameChanged){
                notification_view.text = "Name: ${it.name}"
            }
        })
        //subscribe to mac address event
        PivoEventBus.subscribe(
            PivoEventBus.MAC_ADDRESS, this, Consumer {
                if (it is PivoEvent.MacAddress){
                    notification_view.text = "Mac address: ${it.macAddress}"
                }
            })
        //subscribe to get pivo notifications
        PivoEventBus.subscribe(
            PivoEventBus.PIVO_NOTIFICATION, this, Consumer {
            if (it is PivoEvent.BatteryChanged){
                notification_view.text = "BatteryLevel: ${it.level}"
            }else{
                notification_view.text = "Notification Received"
            }
        })
    }

    private fun getAngle():Int{
        return try {
            val num = angle_view.text.toString()
            num.toInt()
        }catch (e:NumberFormatException){
            90
        }
    }

    override fun onPause() {
        super.onPause()
        //unregister before stopping the activity
        PivoEventBus.unregister(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        PivoProSdk.getInstance().disconnect()
        finish()
    }
}
