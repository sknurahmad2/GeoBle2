package com.example.geoble.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.example.geoble.R

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val channelId = "location_service_channel"

    override fun onCreate() {
        super.onCreate()

        // Initialize the FusedLocationProviderClient to get location updates
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define the location callback, which will be triggered when the location is updated
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                p0 ?: return
                for (location in p0.locations) {
                    // Send location data to the UI and update the notification
                    sendLocationDataToUI(location)
                    updateNotification(location.latitude, location.longitude)
                }
            }
        }

        // Start requesting location updates
        startLocationUpdates()

        // Start as a foreground service (for background location access)
        startForegroundService()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10 seconds
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Check for location services availability
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER)
        val networkProvider = locationManager.getProvider(LocationManager.NETWORK_PROVIDER)

        if (gpsProvider == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            networkProvider == null || !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Handle disabled location services
            Toast.makeText(this, "Location services are disabled. Please enable them.", Toast.LENGTH_LONG).show()
            stopSelf() // Stops the service if location services are disabled
            return
        }

        // Check for location permissions
        if (!hasLocationPermissions()) {
            // Permission not granted, stop the service or handle gracefully
            stopSelf() // Stops the service if permission is not granted
            return
        }

        // Request location updates
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Handle security exception (e.g., log error, display a message)
            Toast.makeText(this, "Location permission denied.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Handle other exceptions (e.g., log error, display a message)
            Toast.makeText(this, "Failed to request location updates.", Toast.LENGTH_LONG).show()
        }
    }

    // Function to check if location permissions are granted
    private fun hasLocationPermissions(): Boolean {
        val fineLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // Check for background location permission (for Android Q and above)
        val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Background location permission is not required for below Android Q
        }

        return fineLocationPermission && coarseLocationPermission && backgroundLocationPermission
    }

    // Function to broadcast location data to the UI
    private fun sendLocationDataToUI(location: Location) {
        val intent = Intent("LocationUpdates")
        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
//        Toast.makeText(this,"${location.latitude} and ${location.longitude}",Toast.LENGTH_SHORT).show()
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // Start the service with the initial notification
    private fun startForegroundService() {
        // Create a notification channel (required for Android 8.0 and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Location Service"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Initial notification with placeholder text
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Tracking Location")
            .setContentText("Waiting for location...")
            .setSmallIcon(R.drawable.ic_my_location)
            .build()

        startForeground(1, notification) // ID of 1 is just an example
    }

    // Method to update the notification with the current latitude and longitude
    private fun updateNotification(latitude: Double, longitude: Double) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Current Location")
            .setContentText("Latitude: $latitude, Longitude: $longitude")
            .setSmallIcon(R.drawable.ic_my_location)
            .build()

        notificationManager?.notify(1, notification) // Updates the existing notification
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This is a started service, so binding is not required
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop location updates when the service is destroyed
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}