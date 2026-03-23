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
    private var isLocationFetching = false
    private var selectedParams: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Restore saved theme BEFORE setContentView ─────────────────
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
        selectedParams = intent.getStringArrayListExtra("selected_params") ?: emptyList()
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
            updateThemeIcon()
        }

        if (mode == "auto") requestLocationAndFetchWeather()
    }

    override fun onResume() {
        super.onResume()
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
        locationCallback = null
        isLocationFetching = false
    }

    // ── Theme Icon ────────────────────────────────────────────────────
    private fun updateThemeIcon() {
        val isDark = getSharedPreferences("eto_prefs", MODE_PRIVATE)
            .getBoolean("is_dark", false)
        binding.btnThemeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
        binding.btnThemeToggle.clearColorFilter()
        binding.btnBack.clearColorFilter()
    }

    // ── Setup UI ──────────────────────────────────────────────────────
    private fun setupUI() {
        if (mode == "auto") {
            binding.tvScreenSubtitle.text = "Auto Weather Fetch"
            binding.tvInputHint.text =
                "Weather data fetched from your location. You can edit values before predicting."
            binding.cardAutoStatus.visibility = View.VISIBLE
            // Show all fields in auto mode
            showAllFields()
        } else {
            binding.tvScreenSubtitle.text = "Manual Input"
            binding.tvInputHint.text = "Fill in all selected fields to predict ETo."
            binding.cardAutoStatus.visibility = View.GONE
            // Show only selected fields in manual mode
            applySelectedFieldVisibility()
        }
    }

    // ── Show all 6 input rows (auto mode) ─────────────────────────────
    private fun showAllFields() {
        binding.rowSunshine.visibility = View.VISIBLE
        binding.rowTmax.visibility     = View.VISIBLE
        binding.rowTmin.visibility     = View.VISIBLE
        binding.rowRHmax.visibility    = View.VISIBLE
        binding.rowRHmin.visibility    = View.VISIBLE
        binding.rowWind.visibility     = View.VISIBLE
    }

    // ── Show only user-selected fields (manual mode) ──────────────────
    private fun applySelectedFieldVisibility() {
        binding.rowSunshine.visibility = if ("n (Sunshine hrs)"   in selectedParams) View.VISIBLE else View.GONE
        binding.rowTmax.visibility     = if ("Tmax (°C)"          in selectedParams) View.VISIBLE else View.GONE
        binding.rowTmin.visibility     = if ("Tmin (°C)"          in selectedParams) View.VISIBLE else View.GONE
        binding.rowRHmax.visibility    = if ("RHmax"              in selectedParams) View.VISIBLE else View.GONE
        binding.rowRHmin.visibility    = if ("RHmin"              in selectedParams) View.VISIBLE else View.GONE
        binding.rowWind.visibility     = if ("u (Windspeed m/s)"  in selectedParams) View.VISIBLE else View.GONE
    }

    // ── Location Permission + Fetch ───────────────────────────────────
    private fun requestLocationAndFetchWeather() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            showLocationSettingsDialog()
            return
        }

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
                binding.tvInputHint.text = "Fill in all fields to predict ETo."
                mode = "manual"
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
        if (isLocationFetching) return
        isLocationFetching = true

        binding.tvAutoStatus.text = "Waiting for GPS..."
        binding.progressAutoFetch.visibility = View.VISIBLE

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.locations.firstOrNull() ?: return
                fusedLocationClient.removeLocationUpdates(this)
                locationCallback = null
                isLocationFetching = false
                weatherAlreadyFetched = true

                binding.tvAutoStatus.text = "GPS acquired (±${loc.accuracy.toInt()}m)"
                fetchAllWeatherData(loc.latitude, loc.longitude)
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

    // ── Fetch Weather from Open-Meteo ─────────────────────────────────
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

    // ── FAO-56 standard wind conversion: u10 → u2 ─────────────────────
    private fun convertWind10mTo2m(u10: Double): Double {
        val u2 = u10 * (4.87 / ln(67.8 * 10.0 - 5.42))
        return Math.round(u2 * 100.0) / 100.0
    }

    // ── Populate Fields from API Response ────────────────────────────
    private fun populateFields(meteo: MeteoResponse, lat: Double, lon: Double) {
        val daily = meteo.daily
        val hourly = meteo.hourly

        // ── Temperature ───────────────────────────────────────────────
        val tmax = daily.temperature_2m_max.firstOrNull() ?: 0.0
        val tmin = daily.temperature_2m_min.firstOrNull() ?: 0.0
        binding.etTmax.setText(String.format("%.1f", tmax))
        binding.etTmin.setText(String.format("%.1f", tmin))

        // ── Wind: 24-hour average → convert u10 to u2 ─────────────────
        val u10 = if (hourly.windspeed_10m.isNotEmpty()) {
            val values = hourly.windspeed_10m.take(24)
            values.sum() / values.size
        } else {
            daily.windspeed_10m_mean.firstOrNull() ?: 0.0
        }
        val u2 = convertWind10mTo2m(u10)
        binding.etWind.setText(String.format("%.2f", u2))

        // ── Humidity ──────────────────────────────────────────────────
        val rhValues = hourly.relativehumidity_2m.take(24)
        val rhmax = rhValues.maxOrNull()?.toDouble() ?: 70.0
        val rhmin = rhValues.minOrNull()?.toDouble() ?: 30.0
        binding.etRHmax.setText(String.format("%.0f", rhmax))
        binding.etRHmin.setText(String.format("%.0f", rhmin))

        // ── Sunshine hours: full 24h forecast ─────────────────────────
        val n = calculateSunshineHours(lat, hourly.shortwave_radiation)
        binding.etSunshine.setText(String.format("%.1f", n))

        binding.tvLocationUsed.text = "%.4f°N, %.4f°E".format(lat, lon)
    }

    // ── Sunshine Hours from Radiation ────────────────────────────────
    private fun calculateSunshineHours(lat: Double, hourlyRad: List<Double>): Double {
        val LOW_THRESHOLD  = 120.0   // was 160
        val HIGH_THRESHOLD = 500.0   // was 600

        var totalSunshineHours = 0.0
        val fullDayRad = hourlyRad.take(24)

        for (radiation in fullDayRad) {
            when {
                radiation <= LOW_THRESHOLD  -> totalSunshineHours += 0.0
                radiation >= HIGH_THRESHOLD -> totalSunshineHours += 1.0
                else -> totalSunshineHours +=
                    (radiation - LOW_THRESHOLD) / (HIGH_THRESHOLD - LOW_THRESHOLD)
            }
        }

        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val phi   = lat * PI / 180.0
        val delta = 0.409 * sin(2 * PI * dayOfYear / 365.0 - 1.39)
        val ws    = acos(-tan(phi) * tan(delta))
        val maxDaylightHours = 24.0 / PI * ws

        return totalSunshineHours.coerceIn(0.0, maxDaylightHours)
    }


    // ── Predict ───────────────────────────────────────────────────────
    private fun predict() {
        val params = mutableMapOf<String, Double>()

        if (binding.rowSunshine.visibility == View.VISIBLE)
            binding.etSunshine.text.toString().toDoubleOrNull()
                ?.let { params["n (Sunshine hrs)"] = it }

        if (binding.rowTmax.visibility == View.VISIBLE)
            binding.etTmax.text.toString().toDoubleOrNull()
                ?.let { params["Tmax (°C)"] = it }

        if (binding.rowTmin.visibility == View.VISIBLE)
            binding.etTmin.text.toString().toDoubleOrNull()
                ?.let { params["Tmin (°C)"] = it }

        if (binding.rowRHmax.visibility == View.VISIBLE)
            binding.etRHmax.text.toString().toDoubleOrNull()
                ?.let { params["RHmax"] = it }

        if (binding.rowRHmin.visibility == View.VISIBLE)
            binding.etRHmin.text.toString().toDoubleOrNull()
                ?.let { params["RHmin"] = it }

        if (binding.rowWind.visibility == View.VISIBLE)
            binding.etWind.text.toString().toDoubleOrNull()
                ?.let { params["u (Windspeed m/s)"] = it }

        // Validate all visible fields are filled
        val visibleFields = listOf(
            binding.rowSunshine, binding.rowTmax, binding.rowTmin,
            binding.rowRHmax, binding.rowRHmin, binding.rowWind
        ).count { it.visibility == View.VISIBLE }

        if (params.size < visibleFields) {
            Toast.makeText(this, "Please fill in all fields!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingCard.visibility = View.VISIBLE
        binding.btnPredict.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.etoApi.predictETo(EToNewRequest(params))
                if (response.isSuccessful && response.body() != null) {
                    showResult(response.body()!!)
                } else if (response.code() == 404) {
                    AlertDialog.Builder(this@PredictActivity)
                        .setTitle("No Model Available")
                        .setMessage(
                            "No trained model exists for this exact parameter combination.\n\n" +
                                    "Please go back and either:\n" +
                                    "• Select a different combination of parameters\n" +
                                    "• Use \"Choose by Model\" to pick a valid model directly"
                        )
                        .setPositiveButton("Go Back & Reselect") { _, _ -> finish() }
                        .setNegativeButton("Stay & Edit", null)
                        .show()
                } else if (response.code() == 422) {
                    Toast.makeText(
                        this@PredictActivity,
                        "One or more values are out of valid range. Please check inputs.",
                        Toast.LENGTH_LONG
                    ).show()
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
                    "Connection failed. Wait 30s and retry (API cold start).",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.loadingCard.visibility = View.GONE
                binding.btnPredict.isEnabled = true
            }
        }
    }


    // ── Show Result ───────────────────────────────────────────────────
    private fun showResult(result: EToNewResponse) {
        val loc = binding.tvLocationUsed.text?.toString() ?: ""
        ResultBottomSheet
            .newInstance(
                eto      = result.eto_mm_per_day,
                r2       = result.model_used.r2_test,
                rmse     = result.model_used.rmse,
                features = result.model_used.features,
                rank     = result.model_used.rank,
                warnings = result.warnings,
                loc      = loc
            )
            .show(supportFragmentManager, "ResultBottomSheet")
    }

    // ── Clear Fields ──────────────────────────────────────────────────
    private fun clearFields() {
        binding.etSunshine.text?.clear()
        binding.etTmax.text?.clear()
        binding.etTmin.text?.clear()
        binding.etRHmax.text?.clear()
        binding.etRHmin.text?.clear()
        binding.etWind.text?.clear()
        binding.tvLocationUsed.text = ""
    }
}
