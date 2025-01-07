package com.example.nimbusstack

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationProviderClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isBroadcasting by remember { mutableStateOf(false) }

    // API keys
    val openWeatherApiKey = "36ef3128e19ad273f317d27f0a0b7d2b"

    // State to manage selected map layer
    var selectedLayer by remember { mutableStateOf("precipitation_new") }

    @SuppressLint("MissingPermission")
    fun startBroadcastingLocation() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val database = FirebaseDatabase.getInstance()
                    val locationRef = database.getReference("user_locations/${currentUser.uid}")
                    locationRef.setValue(mapOf("latitude" to location.latitude, "longitude" to location.longitude))
                        .addOnSuccessListener {
                            Log.d("LocationBroadcast", "Location broadcasted successfully.")
                        }
                        .addOnFailureListener {
                            Log.e("LocationBroadcast", "Failed to broadcast location.", it)
                        }
                } else {
                    Log.e("LocationBroadcast", "Location is null.")
                }
            }.addOnFailureListener {
                Log.e("LocationBroadcast", "Failed to retrieve location.", it)
            }
        } else {
            Log.e("LocationBroadcast", "User is not logged in.")
        }
    }

    val locationPermissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startBroadcastingLocation()
            } else {
                Log.e("Permission", "Location permission denied")
            }
        }
    )

    fun checkPermissionsAndBroadcastLocation() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocationPermission) {
            startBroadcastingLocation()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    TextButton(
                        onClick = {
                            FirebaseAuth.getInstance().signOut()
                            onLogout()
                        }
                    ) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(text = "Welcome to the Dashboard!", style = MaterialTheme.typography.headlineLarge)

            Spacer(modifier = Modifier.height(24.dp))

            Text("Google Maps with OpenWeather Layers:")

            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown menu to select map layer
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = !expanded }) {
                    Text("Selected Layer: $selectedLayer")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val layers = listOf("precipitation_new", "temperature_new", "clouds_new", "wind_new", "pressure_new")
                    layers.forEach { layer ->
                        DropdownMenuItem(
                            text = { Text(layer) },
                            onClick = {
                                selectedLayer = layer
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GoogleMapsWithWeatherLayersView(
                openWeatherApiKey = openWeatherApiKey,
                selectedLayer = selectedLayer
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Broadcast Location")
                Switch(
                    checked = isBroadcasting,
                    onCheckedChange = { isChecked ->
                        isBroadcasting = isChecked
                        if (isChecked) {
                            checkPermissionsAndBroadcastLocation()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GoogleMapsWithWeatherLayersView(
    openWeatherApiKey: String,
    selectedLayer: String
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d("MapDebug", "Map page loaded successfully.")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        Log.e(
                            "MapDebug",
                            "Error loading map: $description (Code: $errorCode, URL: $failingUrl)"
                        )
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                Log.d("MapDebug", "Loading map with selected layer: $selectedLayer")

                val mapHtml = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <style>
            html, body {
                height: 100%;
                margin: 0;
                padding: 0;
            }
            #map {
                height: 100%;
                width: 100%;
            }
        </style>
        <link rel="stylesheet" href="https://unpkg.com/leaflet/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>
    </head>
    <body>
        <div id="map"></div>
        <script>
            try {
                console.log("Initializing map...");
                const apiKey = "$openWeatherApiKey";
                const philippinesCoordinates = [12.8797, 121.7740];
                const zoomLevel = 6;

                const map = L.map('map').setView(philippinesCoordinates, zoomLevel);
                console.log("Map initialized.");

                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(map);

                console.log("Base tile layer added.");

                const weatherLayerUrl = `https://maps.openweathermap.org/maps/2.0/weather/${selectedLayer}/{z}/{x}/{y}?appid=${"$"}{apiKey}`;
                L.tileLayer(weatherLayerUrl, {
                    attribution: 'Weather data &copy; OpenWeather',
                }).addTo(map);
                console.log("Weather layer added: " + weatherLayerUrl);
            } catch (error) {
                console.error("Error in map script:", error);
            }
        </script>
    </body>
    </html>
"""
                loadDataWithBaseURL(null, mapHtml, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    DashboardScreen(onLogout = {})
}
