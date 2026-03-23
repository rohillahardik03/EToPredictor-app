package com.eto.predictor

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eto.predictor.databinding.ActivityParameterSelectBinding
import kotlinx.coroutines.launch

class ParameterSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParameterSelectBinding
    private var isModelMode = false
    private var modelAdapter: ModelAdapter? = null
    private var cachedModels: List<ApiModel> = emptyList()  // ← cache models
    private var modelsLoaded = false


    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("eto_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("is_dark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        binding = ActivityParameterSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        updateThemeIcon()
        binding.btnThemeToggle.setOnClickListener {
            val p = getSharedPreferences("eto_prefs", MODE_PRIVATE)
            val newDark = !p.getBoolean("is_dark", false)
            p.edit { putBoolean("is_dark", newDark) }
            AppCompatDelegate.setDefaultNightMode(
                if (newDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.rvModels.layoutManager = LinearLayoutManager(this)

        // Always pre-fetch models in background so validation is instant
        fetchModels()

        binding.btnToggleMode.setOnClickListener {
            isModelMode = !isModelMode
            updateUI()
        }

        updateUI()

        binding.btnConfirmParams.setOnClickListener {
            val selected = getSelectedParameters()
            when {
                selected.isEmpty() ->
                    Toast.makeText(this, "Please select at least one parameter", Toast.LENGTH_SHORT).show()
                selected.size < 3 ->
                    Toast.makeText(this, "Please select at least 3 parameters", Toast.LENGTH_SHORT).show()
                else -> validateAndProceed(selected)
            }
        }


    }

    private fun validateAndProceed(selected: List<String>) {
        if (!modelsLoaded) {
            Toast.makeText(this, "Still loading models, please wait...", Toast.LENGTH_SHORT).show()
            return  // ← hard block instead of bypassing
        }

        val selectedSet = selected.toSet()
        val matchingModel = cachedModels.find { model ->
            model.input_cols.toSet() == selectedSet
        }

        if (matchingModel != null) {
            navigateToPredictActivity(selected, matchingModel.id, matchingModel.rank)
        } else {
            val supersetModel = cachedModels.find { model ->
                selectedSet.containsAll(model.input_cols.toSet())
            }
            if (supersetModel != null) {
                navigateToPredictActivity(selected, supersetModel.id, supersetModel.rank)
            } else {
                showNoModelDialog(selected)
            }
        }
    }


    private fun showNoModelDialog(selected: List<String>) {
        // Find closest valid combinations to suggest
        val selectedSet = selected.toSet()
        val suggestions = cachedModels
            .sortedByDescending { model ->
                model.input_cols.count { it in selectedSet }
            }
            .take(3)
            .joinToString("\n") { "• ${it.id} (${it.features})" }

        AlertDialog.Builder(this)
            .setTitle("No Model Available")
            .setMessage(
                "No trained model exists for the combination:\n" +
                        selected.joinToString(", ") + "\n\n" +
                        "Closest available models:\n$suggestions\n\n" +
                        "Tip: Use \"Choose by Model\" to pick a valid combination directly."
            )
            .setPositiveButton("Choose by Model") { _, _ ->
                isModelMode = true
                updateUI()
            }
            .setNegativeButton("Reselect", null)
            .show()
    }

    private fun navigateToPredictActivity(
        selected: List<String>,
        modelId: String = "",
        modelRank: Int = 0
    ) {
        startActivity(Intent(this, PredictActivity::class.java).apply {
            putStringArrayListExtra("selected_params", ArrayList(selected))
            putExtra("mode", "manual")
            if (modelId.isNotEmpty()) putExtra("model_id", modelId)
            if (modelRank > 0) putExtra("model_rank", modelRank)
        })
    }

    private fun fetchModels() {
        binding.progressModels.visibility = View.VISIBLE
        binding.btnConfirmParams.isEnabled = false  // ← disable until loaded
        lifecycleScope.launch {
            try {
                cachedModels = RetrofitClient.etoApi.getModels().models
                modelsLoaded = true
                modelAdapter = ModelAdapter(cachedModels) { selectedModel ->
                    startActivity(
                        Intent(this@ParameterSelectActivity, PredictActivity::class.java).apply {
                            putStringArrayListExtra("selected_params", ArrayList(selectedModel.input_cols))
                            putExtra("model_id", selectedModel.id)
                            putExtra("model_rank", selectedModel.rank)
                            putExtra("mode", "manual")
                        }
                    )
                }
                binding.rvModels.adapter = modelAdapter
            } catch (e: Exception) {
                Toast.makeText(
                    this@ParameterSelectActivity,
                    "Failed to load models. Check internet and try again.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressModels.visibility = View.GONE
                binding.btnConfirmParams.isEnabled = true   // ← re-enable after load
            }
        }
    }


    private fun updateThemeIcon() {
        val isDark = getSharedPreferences("eto_prefs", MODE_PRIVATE).getBoolean("is_dark", false)
        binding.btnThemeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
    }

    private fun updateUI() {
        if (isModelMode) {
            binding.btnToggleMode.text = "Choose Manually Instead"
            binding.rvModels.visibility = View.VISIBLE
            binding.progressModels.visibility = if (modelAdapter == null) View.VISIBLE else View.GONE
            binding.layoutManualParams.visibility = View.GONE
            binding.btnConfirmParams.visibility = View.GONE
        } else {
            binding.btnToggleMode.text = "Choose by Model Instead"
            binding.rvModels.visibility = View.GONE
            binding.progressModels.visibility = View.GONE
            binding.layoutManualParams.visibility = View.VISIBLE
            binding.btnConfirmParams.visibility = View.VISIBLE
        }
    }

    private fun getSelectedParameters(): List<String> {
        val selected = mutableListOf<String>()
        val checkboxMap = mapOf(
            binding.cbSunshine to "n (Sunshine hrs)",
            binding.cbTmax     to "Tmax (°C)",
            binding.cbTmin     to "Tmin (°C)",
            binding.cbRHmax    to "RHmax",
            binding.cbRHmin    to "RHmin",
            binding.cbU2       to "u (Windspeed m/s)"
        )
        checkboxMap.forEach { (cb, key) -> if (cb.isChecked) selected.add(key) }
        return selected
    }
}
