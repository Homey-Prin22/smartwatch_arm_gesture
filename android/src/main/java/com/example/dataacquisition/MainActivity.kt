package com.example.dataacquisition

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val btnAcquisition = findViewById<Button>(R.id.btnAcquisition)
        val btnControl = findViewById<Button>(R.id.btnControl)

        btnAcquisition.setOnClickListener {
            startActivity(Intent(this, AcquisitionActivity::class.java))
        }

        btnControl.setOnClickListener {
            startActivity(Intent(this, ControlInterfaceActivity::class.java))
        }
    }
}
