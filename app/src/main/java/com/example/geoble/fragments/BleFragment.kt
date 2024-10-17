package com.example.geoble.fragments

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.geoble.R
import com.example.geoble.activities.DeviceDetailsActivity
import com.example.geoble.databinding.FragmentMyLocationBinding

class BleFragment : Fragment(R.layout.fragment_ble) {

    private lateinit var listView: ListView
    private val deviceList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private var selectedDevice: BluetoothDevice? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var locationManager: LocationManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    private val deviceMap = mutableMapOf<String, Long>()
    private val SCAN_PERIOD: Long = 10000 // 10 seconds, adjust as needed

    private var isBleScannerEnabled = false

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1002
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_LOCATION_SETTINGS = 2
        private const val TAG = "BLEScanner"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        connectivityManager = requireContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView = view.findViewById(R.id.devices_list)
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = adapter

        val selectedDeviceLabel: TextView = view.findViewById(R.id.selected_device_label)
        val connectButton: Button = view.findViewById(R.id.connect_button)

        // Initialize Bluetooth Adapter
        val bluetoothManager = requireContext().getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        view.findViewById<Button>(R.id.scan_button).setOnClickListener {
            checkPermissionsAndStartScan()
        }

        // Connect button click listener
        connectButton.setOnClickListener {
            connectToSelectedDevice()
        }

        // Device selection listener
        listView.setOnItemClickListener { _, _, position, _ ->
            val deviceInfo = deviceList[position]
            selectedDevice = bluetoothAdapter.getRemoteDevice(deviceInfo.substringAfter("(").substringBefore(")"))
            selectedDeviceLabel.text = "Selected Device: ${selectedDevice?.name ?: "Unknown"}"
            Toast.makeText(requireContext(), "Selected: ${selectedDevice?.name ?: "Unknown"}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        if(isBleScannerEnabled){
            stopBleScan() // Stop the scan if the fragment is no longer visible
        }
        super.onPause()
    }

    override fun onDestroyView() {
        if(isBleScannerEnabled){
            stopBleScan() // Stop the scan if the fragment is no longer visible
        }
        super.onDestroyView()
    }

    private fun checkPermissionsAndStartScan() {
        val permissionsToRequest = mutableListOf<String>()

        // Check necessary permissions for BLE scanning
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            isLocationEnabled()
        }
    }

    private fun isLocationEnabled() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        ) {
            isBluetoothEnabled()
        } else {
            Toast.makeText(requireContext(), "Turn on the location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isBluetoothEnabled() {
        // Check if the device supports Bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
        } else {
            // Check if Bluetooth is enabled
            if (bluetoothAdapter?.isEnabled == true) {
                Toast.makeText(requireContext(), "Scanning for BLE devices...", Toast.LENGTH_SHORT).show()
                startBleScan()
            } else {
                Toast.makeText(requireContext(), "Please turn on bluetooth", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun startBleScan() {
        isBleScannerEnabled = true
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        bluetoothLeScanner.startScan(leScanCallback)
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address
            val deviceInfo = "$deviceName ($deviceAddress)"

            // Update the timestamp for the device
            deviceMap[deviceInfo] = System.currentTimeMillis()

            // Add the device to the list if not already present
            if (!deviceList.contains(deviceInfo)) {
                deviceList.add(deviceInfo)
                adapter.notifyDataSetChanged()
            }

            removeOldDevices()
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }

    private fun stopBleScan() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothLeScanner.stopScan(leScanCallback)
    }

    private fun connectToSelectedDevice() {
        selectedDevice?.let {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Bluetooth permissions not granted", Toast.LENGTH_SHORT).show()
                return
            }

            // Start the DeviceDetailsActivity and pass the selected device's information
            val intent = Intent(requireContext(), DeviceDetailsActivity::class.java).apply {
                putExtra("device_name", it.name ?: "Unknown Device")
                putExtra("device_address", it.address)
            }
            startActivity(intent)
        } ?: run {
            Toast.makeText(requireContext(), "No device selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOldDevices() {
        val currentTime = System.currentTimeMillis()
        val iterator = deviceMap.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            // If the device was last seen more than SCAN_PERIOD milliseconds ago, remove it
            if (currentTime - entry.value > SCAN_PERIOD) {
                deviceList.remove(entry.key)
                iterator.remove() // Remove from the map as well
            }
        }
        adapter.notifyDataSetChanged()
    }
}