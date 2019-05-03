package com.vincentmasselis.demoapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding2.view.clicks
import com.vincentmasselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var currentState = BehaviorSubject.createDefault<States>(States.NotScanning)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentState
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                @Suppress("UNREACHABLE_CODE") val ignoreMe = when (it) {
                    States.NotScanning -> {
                        start_scan_button.visibility = View.VISIBLE
                        scanning_text_view.visibility = View.GONE
                        scan_recycler_view.visibility = View.GONE
                    }
                    States.StartingScan -> {
                        start_scan_button.visibility = View.GONE
                        scanning_text_view.visibility = View.VISIBLE
                        scan_recycler_view.visibility = View.GONE
                    }
                    States.Scanning -> {
                        start_scan_button.visibility = View.GONE
                        scanning_text_view.visibility = View.GONE
                        scan_recycler_view.visibility = View.VISIBLE
                    }
                }
            }
            .disposeOnState(ActivityState.DESTROY, this)

        scan_recycler_view.layoutManager = LinearLayoutManager(this)
        scan_recycler_view.adapter = ScanResultAdapter(layoutInflater)

        start_scan_button.clicks()
            .subscribe { startScan() }
            .disposeOnState(ActivityState.DESTROY, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_ENABLE_LOCATION -> if (resultCode == Activity.RESULT_OK) startScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CODE_COARSE_LOCATION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) startScan()
        }
    }

    private fun startScan() {
        currentState.onNext(States.StartingScan)
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan(this, flushEvery = 1L to TimeUnit.SECONDS)
            .doOnNext { currentState.onNext(States.Scanning) }
            .subscribe({
                (scan_recycler_view.adapter as ScanResultAdapter).append(it)
            }, {
                currentState.onNext(States.NotScanning)
                when (it) {
                    is DeviceDoesNotSupportBluetooth -> AlertDialog.Builder(this).setTitle("The current device doesn't support bluetooth le").show()
                    is NeedLocationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_CODE_COARSE_LOCATION)
                    is BluetoothIsTurnedOff -> AlertDialog.Builder(this).setTitle("Bluetooth is turned off").show()
                    is LocationServiceDisabled -> startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_ENABLE_LOCATION)
                    else -> AlertDialog.Builder(this).setTitle("Error occurred: $it").show()
                }
            })
            .disposeOnState(ActivityState.DESTROY, this)
    }

    private sealed class States {
        object NotScanning : States()
        object StartingScan : States()
        object Scanning : States()
    }

    companion object {
        private const val PERMISSION_CODE_COARSE_LOCATION = 1
        private const val REQUEST_CODE_ENABLE_LOCATION = 2
    }
}