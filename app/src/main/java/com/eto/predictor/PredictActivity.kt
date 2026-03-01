package com.eto.predictor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import kotlin.math.sin
import kotlin.math.tan
import androidx.appcompat.app.AppCompatDelegate


class PredictActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mode = "manual"
    private val LOCATION_PERMISSION_REQUEST = 1001
    private var locationCallback: LocationCallback? = null
    private val WEATHER_API_KEY = "fa35259102cd4e7fab2105027260103"

    private val OWM_API_KEY = "4f4e81467e0569dba033a666f2c968ab"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra("mode") ?: "manual"
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPredict.setOnClickListener { predict() }
        binding.btnClear.setOnClickListener { clearFields() }

        // ── Theme Toggle ──────────────────────────────────────────────────
        binding.btnThemeToggle.setOnClickListener {
            val currentMode = AppCompatDelegate.getDefaultNightMode()
            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        if (mode == "auto") requestLocationAndFetchWeather()
    }


    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
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
            binding.tvAutoStatus.text = "❌ Location permission denied."
            binding.progressAutoFetch.visibility = View.GONE
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndFetch() {
        binding.tvAutoStatus.text = "📍 Waiting for GPS... (go near window)"
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
                        binding.tvAutoStatus.text = "✅ GPS acquired (±${loc.accuracy.toInt()}m)"
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
                    binding.tvAutoStatus.text = "📍 Searching GPS... ${seconds}s (go outdoors)"
                    binding.root.postDelayed(this, 5000L)
                }
            }
        }
        binding.root.postDelayed(statusRunnable, 5000L)
    }

    private fun fetchAllWeatherData(lat: Double, lon: Double) {
        binding.tvAutoStatus.text = "☁️ Fetching today's weather..."
        binding.progressAutoFetch.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Fetch both APIs in parallel
                val meteoResponse = RetrofitClient.meteoApi.getTodayData(lat, lon)
                val owmResponse = RetrofitClient.owmApi.getCurrentWeather(
                    lat, lon, OWM_API_KEY
                )

                if (meteoResponse.isSuccessful && meteoResponse.body() != null) {
                    // Get real wind from OWM if available, else fallback to Open-Meteo
                    val realWind = if (owmResponse.isSuccessful && owmResponse.body() != null) {
                        // OWM gives 10m wind → convert to 2m (FAO-56)
                        owmResponse.body()!!.wind.speed
                    } else {
                        // Fallback: Open-Meteo with correction factor
                        val u10mean = meteoResponse.body()!!.daily.windspeed_10m_mean.firstOrNull() ?: 0.0
                        u10mean
                    }

                    populateFields(meteoResponse.body()!!, lat, lon, realWind)
                    binding.tvAutoStatus.text = "✅ Weather loaded successfully"
                } else {
                    binding.tvAutoStatus.text = "❌ Could not fetch weather. Enter manually."
                }
            } catch (e: Exception) {
                binding.tvAutoStatus.text = "❌ Network error. Enter values manually."
            } finally {
                binding.progressAutoFetch.visibility = View.GONE
            }
        }
    }


    private fun populateFields(meteo: MeteoResponse, lat: Double, lon: Double, windU2: Double) {
        val daily = meteo.daily
        val hourly = meteo.hourly

        // ── Tmax and Tmin ─────────────────────────────────────────────────
        val tmax = daily.temperature_2m_max.firstOrNull() ?: 0.0
        val tmin = daily.temperature_2m_min.firstOrNull() ?: 0.0
        binding.etTmax.setText(String.format("%.1f", tmax))
        binding.etTmin.setText(String.format("%.1f", tmin))

        // ── Wind — from OpenWeatherMap real station data (FAO-56 2m) ──────
        binding.etWind.setText(String.format("%.1f", windU2))

        // ── RHmax and RHmin from hourly values ────────────────────────────
        val rhValues = hourly.relativehumidity_2m.take(24)
        val rhmax = rhValues.maxOrNull()?.toDouble() ?: 70.0
        val rhmin = rhValues.minOrNull()?.toDouble() ?: 30.0
        binding.etRHmax.setText(String.format("%.0f", rhmax))
        binding.etRHmin.setText(String.format("%.0f", rhmin))

        // ── Sunshine hours — fractional FAO agricultural threshold ────────
        val n = calculateSunshineHours(lat, hourly.direct_radiation)
        binding.etSunshine.setText(String.format("%.1f", n))

        binding.tvLocationUsed.text = "📍 %.4f°N, %.4f°E".format(lat, lon)
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
                else -> {
                    val fraction = (radiation - LOW_THRESHOLD) / (HIGH_THRESHOLD - LOW_THRESHOLD)
                    totalSunshineHours += fraction
                }
            }
        }

        // Cap at max daylight hours for this location/date
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
            Toast.makeText(this, "⚠️ Please enter at least 2 parameters!", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@PredictActivity,
                        "Server error: ${response.code()}. Try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PredictActivity,
                    "Connection failed. Wait 30s and retry.", Toast.LENGTH_LONG).show()
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
