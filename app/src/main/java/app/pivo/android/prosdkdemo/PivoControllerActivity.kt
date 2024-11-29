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
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdkdemo.databinding.ActivityPivoControllerBinding
import io.reactivex.functions.Consumer


class PivoControllerActivity : AppCompatActivity() {

    private val TAG = "PivoControllerActivity"

    private lateinit var binding: ActivityPivoControllerBinding

    companion object{
        val CAMERA_TYPE_MSG_CODE = "CAMERA_TYPE_MSG_CODE"
    }

    private var rotationSpeedPosition=0
    private var remoteSpeedPosition=0
    private var enableBypass = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPivoControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //get Pivo supported speed list
        val rotationSpeedList = PivoProSdk.getInstance().getSupportedSpeeds().toMutableList()

        //get Pivo supported rotate legecy speed list
        val remoteSpeedList = PivoProSdk.getInstance().getSupportRemoteSpeeds().toMutableList()

        //show pivo version
        binding.versionView.text = "Pivo: ${PivoProSdk.getInstance().getDeviceInfo()}"
//        btnGetMacAddress.visibility = if (PivoProSdk.getInstance().version.version>=5)View.VISIBLE else View.GONE

        //rotate continuously to left
        binding.btnLeftConTurn.setOnClickListener { PivoProSdk.getInstance().turnLeftContinuously(rotationSpeedList[rotationSpeedPosition]) }

        //rotate to left
        binding.btnLeftTurn.setOnClickListener { PivoProSdk.getInstance().turnLeft(getAngle(), rotationSpeedList[rotationSpeedPosition]) }

        //rotate continuously to right
        binding.btnRightConTurn.setOnClickListener { PivoProSdk.getInstance().turnRightContinuously(rotationSpeedList[rotationSpeedPosition]) }


        //rotate to right
        binding.btnRightTurn.setOnClickListener { PivoProSdk.getInstance().turnRight(getAngle(), rotationSpeedList[rotationSpeedPosition]) }

        //stop rotating the device
        binding.btnStop.setOnClickListener { PivoProSdk.getInstance().stop() }

        binding.btnRemoteLeftTurn.setOnClickListener { PivoProSdk.getInstance().turnLeftRemote(getRemoteAngle(), remoteSpeedList[remoteSpeedPosition]) }

        binding.btnRemoteRightTurn.setOnClickListener { PivoProSdk.getInstance().turnRightRemote(getRemoteAngle(), remoteSpeedList[remoteSpeedPosition]) }

        //change Pivo name
        binding.btnChangeName.setOnClickListener {
            if (binding.inputPivoName.text.isNotEmpty()){
                PivoProSdk.getInstance().changeName(binding.inputPivoName.text.toString())
            }
        }

        //go camera to check tracking
        binding.btnCamera1.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(CAMERA_TYPE_MSG_CODE, 1)
            startActivity(intent)
        }

        binding.btnCamera2.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(CAMERA_TYPE_MSG_CODE, 2)
            startActivity(intent)
        }

        binding.btnEnbaleBypass.setOnClickListener {
            PivoProSdk.getInstance().enableBypass(!enableBypass)
        }

//        // get mac address
//        btnGetMacAddress.setOnClickListener { PivoProSdk.getInstance().getMacAddress() }

        //speed list view
        binding.speedListView.adapter= ArrayAdapter<Int>(this, android.R.layout.simple_spinner_item, rotationSpeedList)
        binding.speedListView.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, itemPosition: Int, id: Long) {
                rotationSpeedPosition = itemPosition
                PivoProSdk.getInstance().setSpeed(rotationSpeedList[itemPosition])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        //remote speed list view
        binding.remoteSpeedListView.adapter= ArrayAdapter<Int>(this, android.R.layout.simple_spinner_item, remoteSpeedList)
        binding.remoteSpeedListView.onItemSelectedListener = object : OnItemSelectedListener {
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
                    is PivoEvent.RCCamera->binding.notificationView.text = "CAMERA state: ${if(it.state==0)"Press" else "Release"}"
                    is PivoEvent.RCMode->binding.notificationView.text = "MODE: ${if(it.state==0)"Press" else "Release"}"
                    is PivoEvent.RCStop->binding.notificationView.text = "STOP: ${if(it.state==0)"Press" else "Release"}"
                    is PivoEvent.RCRightContinuous->binding.notificationView.text = "RIGHT_CONTINUOUS: ${if(it.state==0)"Press" else "Release"}"
                    is PivoEvent.RCLeftContinuous->binding.notificationView.text = "LEFT_CONTINUOUS: ${if(it.state==0)"Press" else "Release"}"
                    is PivoEvent.RCLeft->binding.notificationView.text = "LEFT: ${if(it.state==0)"Press" else "Release"}"
                    is PivoEvent.RCRight->binding.notificationView.text = "RIGHT: ${if(it.state==0)"Press" else "Release"}"

                    /**
                     * This below events're deprecated
                     *
                    is PivoEvent.RCLeftPressed->binding.notificationView.text = "LEFT_PRESSED"
                    is PivoEvent.RCLeftRelease->binding.notificationView.text = "LEFT_RELEASE"
                    is PivoEvent.RCRightPressed->binding.notificationView.text = "RIGHT_PRESSED"
                    is PivoEvent.RCRightRelease->binding.notificationView.text = "RIGHT_RELEASE"
                    is PivoEvent.RCSpeedUpPressed->binding.notificationView.text = "SPEED_UP_PRESSED: ${it.level}"
                    is PivoEvent.RCSpeedDownPressed->binding.notificationView.text = "SPEED_DOWN_PRESSED: ${it.level}"
                    is PivoEvent.RCSpeedUpRelease->binding.notificationView.text = "SPEED_UP_RELEASE: ${it.level}"
                    is PivoEvent.RCSpeedDownRelease->binding.notificationView.text = "SPEED_DOWN_RELEASE: ${it.level}"
                     */

                    is PivoEvent.RCSpeed->binding.notificationView.text = "SPEED: : ${if(it.state==0)"Press" else "Release"} speed: ${it.level}"
                }
            })
        //subscribe to name change event
        PivoEventBus.subscribe(
            PivoEventBus.NAME_CHANGED, this, Consumer {
                if (it is PivoEvent.NameChanged){
                    binding.notificationView.text = "Name: ${it.name}"
                }
            })
        //subscribe to mac address event
        PivoEventBus.subscribe(
            PivoEventBus.MAC_ADDRESS, this, Consumer {
                if (it is PivoEvent.MacAddress){
                    binding.notificationView.text = "Mac address: ${it.macAddress}"
                }
            })
        //subscribe to get pivo notifications
        PivoEventBus.subscribe(
            PivoEventBus.PIVO_NOTIFICATION, this, Consumer {
                if (it is PivoEvent.BatteryChanged){
                    binding.notificationView.text = "BatteryLevel: ${it.level}"
                } else if(it is PivoEvent.Rotated) {
                    binding.degreeView.text = "degree: ${it.horizontalMovement.rotationDegree}, speed: ${it.horizontalMovement.speed}, direction: ${it.horizontalMovement.direction}"
                } else{
                    binding.notificationView.text = "Notification Received"
                }
            })

        PivoEventBus.subscribe(
            PivoEventBus.BYPASS, this, Consumer {
                if(it is PivoEvent.BypassEvent) {
                    enableBypass = it.isEnabled
                    binding.btnEnbaleBypass.text = if(it.isEnabled) getString(R.string.disable_bypass) else getString(R.string.enable_bypass)
                }
            })
    }

    private fun getAngle():Int{
        return try {
            val num = binding.rotationAngleView.text.toString()
            num.toInt()
        }catch (e:NumberFormatException){
            90
        }
    }

    private fun getRemoteAngle():Int{
        return try {
            val num = binding.remoteAngleView.text.toString()
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
