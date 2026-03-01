package com.eto.predictor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.eto.predictor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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
