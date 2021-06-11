package me.autolock.m100.cmi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import me.autolock.m100.cmi.databinding.ActivityMainBinding
import org.koin.android.ext.android.bind
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

// used to identify adding bluetooth names
const val REQUEST_ENABLE_BT = 1
// used to request fine location permission
const val REQUEST_ALL_PERMISSION = 2
val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION
)

var handler: Handler? = null

fun outputLogLine(str: String?) {
    handler?.obtainMessage(1, str)?.sendToTarget()
}

fun requestEnableBLE() {
    handler?.obtainMessage(2)?.sendToTarget()
}

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModel<MainViewModel>()
    private var logLine = 0
    private var adapter: BleListAdapter? = null
    private var dialog: FotaDialog? = null

    private val fotaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                intent.data?.let { uri ->
                    dialog = FotaDialog(this)
                    dialog?.setTitle("Loading BIN File...")
                    dialog?.show()
                    val (list, length) = viewModel.loadBinFile(this, uri)
                    viewModel.startOTA(list, length)
                }
            }
        }
    }

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

        handler = Handler(Looper.getMainLooper()) {
            when (it.what) {
                0 -> outputLogText(binding, it.obj as String)
                1 -> {
                    val str = "${logLine}: " + it.obj + "\n"
                    outputLogText(binding, str)
                    logLine++
                }
                2 -> requestEnableBLE()
            }
            true
        }

        binding.bleList.setHasFixedSize(true)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        binding.bleList.layoutManager = layoutManager


        adapter = BleListAdapter()
        binding.bleList.adapter = adapter
        adapter?.setItemClickListener(object : BleListAdapter.ItemClickListener {
            override fun onClick(view: View, device: BluetoothDevice?) {
                if (device != null) {
                    viewModel.connectDevice(device)
                }
            }
        })

        initObserver(binding)

        supportActionBar?.hide()
        binding.statusText.text = APP_VERSION

        binding.fotaButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("FOTA")
            builder.setMessage("Do you want to upgrade F/W?")
            builder.setPositiveButton("OK") { dialog, id ->
                var readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                if (readPermission == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_READ_EXTERNAL_STORAGE)
                }
                else {
                    binding.readSwitch.isChecked = false

                    val intent = Intent()
                        .setType("application/octet-stream")
                        .setAction(Intent.ACTION_GET_CONTENT)
                    fotaLauncher.launch(intent)
                }
            }
            builder.setNegativeButton("Cancel") { dialog, id ->
                // do nothing
            }
            builder.show()
        }
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
        viewModel.connectedBridge.observe(this,{
            viewModel.connected.set(it)
        })
        viewModel.listUpdate.observe(this, {
            adapter?.setItem(it)
            /*it.getContentIfNotHandled()?.let { scanResults ->
                adapter?.setItem(scanResults)
            }*/
        })
        viewModel.reportArray.observe(this, {
            val adc1 = it[2].toPositiveInt() + it[3].toPositiveInt() * 256
            val adc2 = it[4].toPositiveInt() + it[5].toPositiveInt() * 256
            val str = it.toHexString() + " ($adc1, $adc2)"
            binding.reportText.text = str
        })
        viewModel.version.observe(this, {
            binding.versionText.text = it
        })
        viewModel.otaLength.observe(this, {
            dialog?.setLength(it)
        })
        viewModel.otaCurrent.observe(this, {
            dialog?.setCurrent(it)
            if (viewModel.otaLength.value != 0 && viewModel.otaLength.value == viewModel.otaCurrent.value) {
                //dialog?.dismiss()
            }
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
