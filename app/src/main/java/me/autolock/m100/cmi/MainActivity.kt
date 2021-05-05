package me.autolock.m100.cmi

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import me.autolock.m100.cmi.databinding.ActivityMainBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

// used to identify adding bluetooth names
const val REQUEST_ENABLE_BT = 1
// used to request fine location permission
const val REQUEST_ALL_PERMISSION = 2
val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION
)

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModel<MainViewModel>()
    private var logLine = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(
            this,
            R.layout.activity_main
        )
        binding.viewModel = viewModel

        // check if location permission
        if (!hasPermissions(this, PERMISSIONS)) {
            requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
        }

        initObserver(binding)
    }

    private fun outputLogText(binding: ActivityMainBinding, it: String) {
        binding.logText.append(it)
        if ((binding.logText.measuredHeight - binding.logScroll.scrollY) <= (binding.logScroll.height + binding.logText.lineHeight)) {
            binding.logText.post {
                binding.logScroll.smoothScrollTo(0, binding.logText.bottom)
            }
        }
    }

    private fun initObserver(binding: ActivityMainBinding) {
        // When the logText value of the view model is changed, it is displayed in the log textview.
        viewModel.logText.observe(this, {
            outputLogText(binding, it)
        })
        // When the logLine value of the view model is changed, a number is added to the log textview and printed, and the next line is moved.
        viewModel.logLine.observe(this, {
            val str = "${logLine}: " + it + "\n"
            outputLogText(binding, str)
            logLine++
        })
        // When the statusText value of the view model is changed, it is displayed in the status textview.
        viewModel.statusText.observe(this, {
            binding.statusText.text = it
        })
        // When the value of bleRepository.scanning is set, viewModel.scanning is set
        viewModel.scanningBridge.observe(this, {
            viewModel.scanning.set(it)
            /*it.getContentIfNotHandled()?.let { scanning ->
                viewModel.scanning.set(scanning)
            }*/
        })
    }

    override fun onResume() {
        super.onResume()
        // finish app if the BLE is not supported
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish()
        }
    }

    private val requestEnableBleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            // do somthing after enableBleRequest
        }
    }

    private fun requestEnableBLE() {
        val bleEnableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestEnableBleResult.launch(bleEnableIntent)
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
        if (context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

}
