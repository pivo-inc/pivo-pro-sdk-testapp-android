package app.pivo.android.prosdkdemo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import app.pivo.android.basicsdk.events.PivoEvent
import app.pivo.android.basicsdk.events.PivoEventBus
import io.reactivex.functions.Consumer
import app.pivo.android.prosdk.PivoProSdk
import kotlinx.android.synthetic.main.activity_pivo_controller.*


class PivoControllerActivity : AppCompatActivity() {

    private val TAG = "PivoControllerActivity"

    companion object{
        val CAMERA_TYPE_MSG_CODE = "CAMERA_TYPE_MSG_CODE"
    }

    private var rotationSpeedPosition=0
    private var remoteSpeedPosition=0
    private var enableBypass = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pivo_controller)

        //get Pivo supported speed list
        val rotationSpeedList = PivoProSdk.getInstance().getSupportedSpeeds().toMutableList()

        //get Pivo supported rotate legecy speed list
        val remoteSpeedList = PivoProSdk.getInstance().getSupportRemoteSpeeds().toMutableList()

        //show pivo version
        version_view.text = "Pivo: ${PivoProSdk.getInstance().getDeviceInfo()}"
//        btnGetMacAddress.visibility = if (PivoProSdk.getInstance().version.version>=5)View.VISIBLE else View.GONE

        //rotate continuously to left
        btn_left_con_turn.setOnClickListener { PivoProSdk.getInstance().turnLeftContinuously(rotationSpeedList[rotationSpeedPosition]) }

        //rotate to left
        btn_left_turn.setOnClickListener { PivoProSdk.getInstance().turnLeft(getAngle(), rotationSpeedList[rotationSpeedPosition]) }

        //rotate continuously to right
        btn_right_con_turn.setOnClickListener { PivoProSdk.getInstance().turnRightContinuously(rotationSpeedList[rotationSpeedPosition]) }


        //rotate to right
        btn_right_turn.setOnClickListener { PivoProSdk.getInstance().turnRight(getAngle(), rotationSpeedList[rotationSpeedPosition]) }

        //stop rotating the device
        btn_stop.setOnClickListener { PivoProSdk.getInstance().stop() }

        btn_remote_left_turn.setOnClickListener { PivoProSdk.getInstance().turnLeftRemote(getRemoteAngle(), remoteSpeedList[remoteSpeedPosition]) }

        btn_remote_right_turn.setOnClickListener { PivoProSdk.getInstance().turnRightRemote(getRemoteAngle(), remoteSpeedList[remoteSpeedPosition]) }

        //change Pivo name
        btn_change_name.setOnClickListener {
            if (input_pivo_name.text.isNotEmpty()){
                PivoProSdk.getInstance().changeName(input_pivo_name.text.toString())
            }
        }

        //go camera to check tracking
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

        btn_enbale_bypass.setOnClickListener {
            PivoProSdk.getInstance().enableBypass(!enableBypass)
        }

//        // get mac address
//        btnGetMacAddress.setOnClickListener { PivoProSdk.getInstance().getMacAddress() }

        //speed list view
        speed_list_view.adapter= ArrayAdapter<Int>(this, android.R.layout.simple_spinner_item, rotationSpeedList)
        speed_list_view.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, itemPosition: Int, id: Long) {
                rotationSpeedPosition = itemPosition
                PivoProSdk.getInstance().setSpeed(rotationSpeedList[itemPosition])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        //remote speed list view
        remote_speed_list_view.adapter= ArrayAdapter<Int>(this, android.R.layout.simple_spinner_item, remoteSpeedList)
        remote_speed_list_view.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, itemPosition: Int, id: Long) {
                remoteSpeedPosition = itemPosition
                PivoProSdk.getInstance().setSpeed(remoteSpeedList[itemPosition])
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
                } else{
                    notification_view.text = "Notification Received"
                }
            })

        PivoEventBus.subscribe(
            PivoEventBus.BYPASS, this, Consumer {
                if(it is PivoEvent.BypassEvent) {
                    enableBypass = it.isEnabled
                    btn_enbale_bypass.text = if(it.isEnabled) getString(R.string.disable_bypass) else getString(R.string.enable_bypass)
                }
            })
    }

    private fun getAngle():Int{
        return try {
            val num = rotation_angle_view.text.toString()
            num.toInt()
        }catch (e:NumberFormatException){
            90
        }
    }

    private fun getRemoteAngle():Int{
        return try {
            val num = remote_angle_view.text.toString()
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
