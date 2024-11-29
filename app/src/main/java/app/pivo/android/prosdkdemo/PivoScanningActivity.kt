package app.pivo.android.prosdkdemo

import  android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import app.pivo.android.basicsdk.events.PivoEvent
import app.pivo.android.basicsdk.events.PivoEventBus
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdkdemo.databinding.ActivityPivoScanningBinding

class PivoScanningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPivoScanningBinding
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    
    private val TAG = "MainActivity"
    private lateinit var resultAdapter: ScanResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPivoScanningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //initialize device scan adapter
        resultAdapter = ScanResultsAdapter()
        resultAdapter.setOnAdapterItemClickListener(object :
            ScanResultsAdapter.OnAdapterItemClickListener {
            override fun onAdapterViewClick(view: View?) {
                val scanResult = resultAdapter.getItemAtPosition(binding.scanResults.getChildAdapterPosition(view!!))
                if (scanResult!=null){
                    PivoProSdk.getInstance().connectTo(scanResult)
                }
            }
        })

        //prepare scan result listview
        binding.scanResults.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@PivoScanningActivity)
            adapter = resultAdapter
        }
        //start scanning button
        binding.scanButton.setOnClickListener {
            checkPermission()
        }
        //cancel scanning button
        binding.cancelButton.setOnClickListener {
            binding.scanningBar.visibility = View.INVISIBLE
            PivoProSdk.getInstance().stopScan()
            resultAdapter.clearScanResults()
        }

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                // When all permissions are granted
                binding.scanningBar.visibility = View.VISIBLE
                PivoProSdk.getInstance().scan()
            } else {
                // Handling when permission is denied
                // You can explain to the user why the permission is needed and request it again, or take action such as restricting the feature.
            }
        }
    }


    override fun onResume() {
        super.onResume()
        //subscibe pivo connection events
        PivoEventBus.subscribe(PivoEventBus.CONNECTION_COMPLETED, this) {
            binding.scanningBar.visibility = View.INVISIBLE
            if (it is PivoEvent.ConnectionComplete) {
                Log.e(TAG, "CONNECTION_COMPLETED")
                openController()
            }
        }
        //subscribe to get scan device
        PivoEventBus.subscribe(PivoEventBus.SCAN_DEVICE, this) {
            if (it is PivoEvent.Scanning) {
                resultAdapter.addScanResult(it.device)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        //unregister pivo event bus
        PivoEventBus.unregister(this)
    }

    //open pivo controller screen
    private fun openController(){
        startActivity(Intent(this, PivoControllerActivity::class.java))
    }

    //check permissions if they're granted start scanning, otherwise ask to user to grant permissions
    private fun checkPermission(){// alternative Permission library Dexter
        requestPermissionLauncher.launch(permissionList.toTypedArray())
    }
    //permissions which are required for bluetooth
    private var permissionList = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).also {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            it.add(Manifest.permission.BLUETOOTH_SCAN)
            it.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            it.add(Manifest.permission.BLUETOOTH)
            it.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
}
