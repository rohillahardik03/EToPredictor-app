package com.eto.predictor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.eto.predictor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Restore saved theme BEFORE setContentView ─────────────────────
        val prefs = getSharedPreferences("eto_prefs", MODE_PRIVATE)
        val savedDark = prefs.getBoolean("is_dark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (savedDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnYes.setOnClickListener {
            val intent = Intent(this, PredictActivity::class.java)
            intent.putExtra("mode", "manual")
            startActivity(intent)
        }

        binding.btnNo.setOnClickListener {
            val intent = Intent(this, PredictActivity::class.java)
            intent.putExtra("mode", "auto")
            startActivity(intent)
        }
    }
}
