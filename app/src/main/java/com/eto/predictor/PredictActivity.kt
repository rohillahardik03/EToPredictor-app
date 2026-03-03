package com.eto.predictor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.eto.predictor.databinding.ActivityPredictBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

class PredictActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mode = "manual"
    private val LOCATION_PERMISSION_REQUEST = 1001
    private var locationCallback: LocationCallback? = null
    private var weatherAlreadyFetched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Restore saved theme BEFORE setContentView ─────────────────────
        val prefs = getSharedPreferences("eto_prefs", MODE_PRIVATE)
        val savedDark = prefs.getBoolean("is_dark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (savedDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityPredictBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra("mode") ?: "manual"
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        updateThemeIcon()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPredict.setOnClickListener { predict() }
        binding.btnClear.setOnClickListener { clearFields() }

        binding.btnThemeToggle.setOnClickListener {
            val p = getSharedPreferences("eto_prefs", MODE_PRIVATE)
            val newDark = !p.getBoolean("is_dark", false)
            p.edit().putBoolean("is_dark", newDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (newDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        if (mode == "auto") requestLocationAndFetchWeather()
    }

    override fun onResume() {
        super.onResume()
        // Re-attempt fetch when user returns from Location Settings
        if (mode == "auto" && !weatherAlreadyFetched) {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (gpsEnabled || networkEnabled) {
                requestLocationAndFetchWeather()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun updateThemeIcon() {
        val isDark = getSharedPreferences("eto_prefs", MODE_PRIVATE)
            .getBoolean("is_dark", false)
        binding.btnThemeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
        binding.btnThemeToggle.clearColorFilter()
        binding.btnBack.clearColorFilter()
    }

    private fun setupUI() {
        if (mode == "auto") {
            binding.tvScreenSubtitle.text = "Auto Weather Fetch"
            binding.tvInputHint.text =
                "Weather data fetched from your location. You can edit values before predicting."
            binding.cardAutoStatus.visibility = View.VISIBLE
        } else {
            binding.tvScreenSubtitle.text = "Manual Input"
            binding.cardAutoStatus.visibility = View.GONE
        }
    }

    private fun requestLocationAndFetchWeather() {
        // ── Step 1: Check if location hardware is turned on ───────────────
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            showLocationSettingsDialog()
            return
        }

        // ── Step 2: Check if permission is granted ────────────────────────
        val fineGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted || !coarseGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            getLocationAndFetch()
        }
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Required")
            .setMessage("Please turn on location services to auto-fetch weather data for your field.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Enter Manually") { dialog, _ ->
                dialog.dismiss()
                binding.cardAutoStatus.visibility = View.GONE
                binding.tvScreenSubtitle.text = "Manual Input"
                binding.tvInputHint.text =
                    "Enter at least 2 values. Missing values will be auto-estimated."
                mode = "manual"  // prevent onResume from retrying
            }
            .setCancelable(false)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getLocationAndFetch()
        } else {
            binding.tvAutoStatus.text = "Location permission denied."
            binding.progressAutoFetch.visibility = View.GONE
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndFetch() {
        binding.tvAutoStatus.text = "Waiting for GPS..."
        binding.progressAutoFetch.visibility = View.VISIBLE

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    if (loc != null) {
                        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
                        weatherAlreadyFetched = true
                        binding.tvAutoStatus.text = "GPS acquired (±${loc.accuracy.toInt()}m)"
                        fetchAllWeatherData(loc.latitude, loc.longitude)
                        return
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback!!, Looper.getMainLooper()
        )

        var seconds = 0
        val statusRunnable = object : Runnable {
            override fun run() {
                seconds += 5
                if (binding.progressAutoFetch.visibility == View.VISIBLE) {
                    binding.tvAutoStatus.text = "Searching GPS... ${seconds}s"
                    binding.root.postDelayed(this, 5000L)
                }
            }
        }
        binding.root.postDelayed(statusRunnable, 5000L)
    }

    private fun fetchAllWeatherData(lat: Double, lon: Double) {
        binding.tvAutoStatus.text = "Fetching today's weather..."
        binding.progressAutoFetch.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val meteoResponse = RetrofitClient.meteoApi.getTodayData(lat, lon)

                if (meteoResponse.isSuccessful && meteoResponse.body() != null) {
                    populateFields(meteoResponse.body()!!, lat, lon)
                    binding.tvAutoStatus.text = "Weather loaded successfully"
                } else {
                    binding.tvAutoStatus.text = "Could not fetch weather. Enter manually."
                }
            } catch (e: Exception) {
                binding.tvAutoStatus.text = "Network error. Enter values manually."
            } finally {
                binding.progressAutoFetch.visibility = View.GONE
            }
        }
    }

    /**
     * FAO-56 log-law: u2 = u10 × ln(2/z0) / ln(10/z0)
     * z0 = 0.025m for agricultural crop field (Punjab conditions)
     */
    private fun convertWind10mTo2m(u10: Double): Double {
        val z0 = 0.025
        val u2 = u10 * (ln(2.0 / z0) / ln(10.0 / z0))
        return Math.round(u2 * 100.0) / 100.0
    }

    private fun populateFields(meteo: MeteoResponse, lat: Double, lon: Double) {
        val daily = meteo.daily
        val hourly = meteo.hourly

        // ── Temperature ───────────────────────────────────────────────────
        val tmax = daily.temperature_2m_max.firstOrNull() ?: 0.0
        val tmin = daily.temperature_2m_min.firstOrNull() ?: 0.0
        binding.etTmax.setText(String.format("%.1f", tmax))
        binding.etTmin.setText(String.format("%.1f", tmin))

        // ── Wind: current hour's value, not daily mean ────────────────────
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val u10 = if (hourly.windspeed_10m.size > currentHour) {
            hourly.windspeed_10m[currentHour]
        } else {
            daily.windspeed_10m_mean.firstOrNull() ?: 0.0
        }
        val u2 = convertWind10mTo2m(u10)
        binding.etWind.setText(String.format("%.2f", u2))

        // ── Humidity ──────────────────────────────────────────────────────
        val rhValues = hourly.relativehumidity_2m.take(24)
        val rhmax = rhValues.maxOrNull()?.toDouble() ?: 70.0
        val rhmin = rhValues.minOrNull()?.toDouble() ?: 30.0
        binding.etRHmax.setText(String.format("%.0f", rhmax))
        binding.etRHmin.setText(String.format("%.0f", rhmin))

        // ── Sunshine hours ────────────────────────────────────────────────
        val n = calculateSunshineHours(lat, hourly.direct_radiation)
        binding.etSunshine.setText(String.format("%.1f", n))

        binding.tvLocationUsed.text = "%.4f°N, %.4f°E".format(lat, lon)
    }

    private fun calculateSunshineHours(lat: Double, hourlyRad: List<Double>): Double {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val observedRad = hourlyRad.take(currentHour + 1)

        val LOW_THRESHOLD = 200.0
        val HIGH_THRESHOLD = 600.0
        var totalSunshineHours = 0.0

        for (radiation in observedRad) {
            when {
                radiation <= LOW_THRESHOLD -> totalSunshineHours += 0.0
                radiation >= HIGH_THRESHOLD -> totalSunshineHours += 1.0
                else -> totalSunshineHours +=
                    (radiation - LOW_THRESHOLD) / (HIGH_THRESHOLD - LOW_THRESHOLD)
            }
        }

        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val phi = lat * PI / 180.0
        val delta = 0.409 * sin(2 * PI * dayOfYear / 365.0 - 1.39)
        val ws = acos(-tan(phi) * tan(delta))
        val maxDaylightHours = 24.0 / PI * ws

        return totalSunshineHours.coerceIn(0.0, maxDaylightHours)
    }

    private fun predict() {
        val n = binding.etSunshine.text.toString().toDoubleOrNull()
        val tmax = binding.etTmax.text.toString().toDoubleOrNull()
        val tmin = binding.etTmin.text.toString().toDoubleOrNull()
        val rhmax = binding.etRHmax.text.toString().toDoubleOrNull()
        val rhmin = binding.etRHmin.text.toString().toDoubleOrNull()
        val u = binding.etWind.text.toString().toDoubleOrNull()

        if (listOfNotNull(n, tmax, tmin, rhmax, rhmin, u).size < 2) {
            Toast.makeText(this, "Please enter at least 2 parameters!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingCard.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
        binding.btnPredict.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = EToRequest(n, tmax, tmin, rhmax, rhmin, u)
                val response = RetrofitClient.etoApi.predictETo(request)
                if (response.isSuccessful && response.body() != null) {
                    showResult(response.body()!!)
                } else {
                    Toast.makeText(
                        this@PredictActivity,
                        "Server error: ${response.code()}. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@PredictActivity,
                    "Connection failed. Wait 30s and retry.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.loadingCard.visibility = View.GONE
                binding.btnPredict.isEnabled = true
            }
        }
    }

    private fun showResult(result: EToResponse) {
        val loc = binding.tvLocationUsed.text?.toString() ?: ""
        ResultBottomSheet
            .newInstance(result.eto, result.params_provided, loc)
            .show(supportFragmentManager, "ResultBottomSheet")
    }

    private fun clearFields() {
        binding.etSunshine.text?.clear()
        binding.etTmax.text?.clear()
        binding.etTmin.text?.clear()
        binding.etRHmax.text?.clear()
        binding.etRHmin.text?.clear()
        binding.etWind.text?.clear()
        binding.resultCard.visibility = View.GONE
        binding.tvLocationUsed.text = ""
    }
}
