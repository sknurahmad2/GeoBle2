package com.example.geoble.fragments

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.geoble.R
import com.example.geoble.services.LocationService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.progressindicator.LinearProgressIndicator

class MyLocationFragment : Fragment(R.layout.fragment_my_location) {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var locationManager: LocationManager
    private val LOCATION_PERMISSION_CODE = 1000
    private val forgroundCoarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
    private val forgroundFineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var loadingBar: LinearProgressIndicator
    private lateinit var myButton: Button
    private var isServiceStarted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return inflater.inflate(R.layout.fragment_my_location, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize TextViews for updating location
        latitudeTextView = view.findViewById(R.id.tvLatitude)
        longitudeTextView = view.findViewById(R.id.tvLongitude)
        myButton = view.findViewById(R.id.myButton)
        loadingBar = view.findViewById(R.id.linearProgressIndicator)

        // Set up button click listener
        myButton.setOnClickListener {
//            loadingBar.visibility = View.VISIBLE
            requestAllPermissions()
        }
    }
    override fun onResume() {
        super.onResume()
        if (isServiceStarted) {
            loadingBar.visibility = View.VISIBLE
            startLocationService()
            isServiceStarted = true
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(locationReceiver, IntentFilter("LocationUpdates"))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop location service when fragment is paused to save resources
        stopLocationService()
        // Unregister the broadcast receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
    }

    private fun startLocationService() {
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().startService(serviceIntent)
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().stopService(serviceIntent)
        isServiceStarted = false
    }

    // BroadcastReceiver to receive location updates from the service
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val latitude = intent?.getDoubleExtra("latitude", Double.NaN)
            val longitude = intent?.getDoubleExtra("longitude", Double.NaN)

            // Update the UI with received location data
            if (latitude != null && !latitude.isNaN() && longitude != null && !longitude.isNaN()) {
                latitudeTextView.text = "Latitude: $latitude"
                longitudeTextView.text = "Longitude: $longitude"
                loadingBar.visibility = View.INVISIBLE
            } else {
                loadingBar.visibility = View.INVISIBLE
                Toast.makeText(requireContext(), "Failed to retrieve location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (!hasCoarseLocationPermission()) {
            permissionsToRequest.add(forgroundCoarseLocationPermission)
        }

        if (!hasFineLocationPermission()) {
            permissionsToRequest.add(forgroundFineLocationPermission)
        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackGroundLocationPermission()) {
//            permissionsToRequest.add(backgroundLocationPermission)
//        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_CODE)
        } else {
            requestBackgroundLocation()
        }
    }

    private fun requestBackgroundLocation(){
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackGroundLocationPermission()) {
            permissionsToRequest.add(backgroundLocationPermission)
        }

        if (permissionsToRequest.isNotEmpty()) {
            Toast.makeText(requireContext(), "Please allow location permission all the time", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_CODE)
        } else {
            requestNotification()
        }
    }

    private fun requestNotification(){
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Toast.makeText(requireContext(), "Allow notification permission", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_CODE)
        } else {
            isInternetAvailable()
        }
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            forgroundCoarseLocationPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            forgroundFineLocationPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackGroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            backgroundLocationPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isInternetAvailable() {
        val activeNetwork = connectivityManager.activeNetworkInfo
        if (activeNetwork != null && activeNetwork.isConnected) {
            checkIfLocationEnabled()
        } else {
            Toast.makeText(requireContext(), "Turn on the internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkIfLocationEnabled() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            Toast.makeText(requireContext(), "Please wait for few seconds...", Toast.LENGTH_SHORT)
                .show()
            loadingBar.visibility = View.VISIBLE
            startLocationService()
        } else {
            Toast.makeText(requireContext(), "Turn on the location", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with location request
                isInternetAvailable()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
