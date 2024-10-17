package com.example.geoble.fragments

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
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
import androidx.core.location.LocationManagerCompat.isLocationEnabled
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.geoble.R
import com.example.geoble.databinding.FragmentMyLocationBinding
import com.example.geoble.services.LocationService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.material.progressindicator.LinearProgressIndicator

class MyLocationFragment : Fragment(R.layout.fragment_my_location) {
//    private var _binding: FragmentMyLocationBinding? = null
//    private val binding get() = _binding!!
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var locationManager: LocationManager
    private val LOCATION_PERMISSION_CODE = 1001
    private val forgroundCoarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
    private val forgroundFineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var loadingBar: LinearProgressIndicator
    private lateinit var myButton : Button
    private var isServiceStarted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
//        _binding = FragmentMyLocationBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        connectivityManager = requireContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
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
//          Toast.makeText(requireContext(),"My : ${Build.VERSION.SDK_INT} Q : ${Build.VERSION_CODES.Q}",Toast.LENGTH_LONG).show()
            loadingBar.visibility = View.VISIBLE
            requestAllPermission()
        }

    }

    override fun onResume() {
        super.onResume()
        if (!isServiceStarted) {
            loadingBar.visibility = View.VISIBLE
            startLocationService()
            isServiceStarted = true
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(locationReceiver, IntentFilter("LocationUpdates"))
    }

    override fun onPause() {
        super.onPause()
        // Unregister the broadcast receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the broadcast receiver to avoid memory leaks
        stopLocationService()
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

//     BroadcastReceiver to receive location updates from the service
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val latitude = intent?.getDoubleExtra("latitude", 0.0)
            val longitude = intent?.getDoubleExtra("longitude", 0.0)

            // Update the UI with received location data
            loadingBar.visibility = View.INVISIBLE
            latitudeTextView.text = "Latitude: $latitude"
            longitudeTextView.text = "Longitude: $longitude"
        }
    }

    private fun requestAllPermission(){
        var permissionToRequest = mutableListOf<String>()

        if(!hasCoarseLocationPermission()){
            permissionToRequest.add(forgroundCoarseLocationPermission)
        }

        if(!hasFineLocationPermission()){
            permissionToRequest.add(forgroundFineLocationPermission)
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackGroundLocationPermission()){
            permissionToRequest.add(backgroundLocationPermission)
        }

        if(permissionToRequest.isNotEmpty()){
            // Request the permissions
            ActivityCompat.requestPermissions(requireActivity(), permissionToRequest.toTypedArray(), LOCATION_PERMISSION_CODE)
        }else{
            isInternetAvailable()
        }
    }

    private fun hasCoarseLocationPermission() : Boolean{
        return ActivityCompat.checkSelfPermission(requireContext(),forgroundCoarseLocationPermission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission() : Boolean{
        return ActivityCompat.checkSelfPermission(requireContext(), forgroundFineLocationPermission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackGroundLocationPermission() : Boolean{
        return ActivityCompat.checkSelfPermission(requireContext(),backgroundLocationPermission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isInternetAvailable() {
        val activeNetwork = connectivityManager.activeNetworkInfo
        if (activeNetwork != null && activeNetwork.isConnected) {
            isLocationEnabled()
        } else {
            Toast.makeText(requireContext(), "Turn on the internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        ) {
            startLocationService()
        } else {
            Toast.makeText(requireContext(), "Turn on the location", Toast.LENGTH_SHORT).show()
        }
    }
}