package com.example.trashdata

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.trashdata.R


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnForgotten = findViewById<Button>(R.id.btnForgotten)
        val txtResult = findViewById<TextView>(R.id.txtResult)

        btnScan.setOnClickListener {
            txtResult.text = "Scan started..."
        }

        btnForgotten.setOnClickListener {
            txtResult.text = "Showing forgotten files..."
        }
    }
}
