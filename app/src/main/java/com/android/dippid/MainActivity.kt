package com.android.dippid

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    var sendingActive: Boolean = false

    var ipAddress: String = ""
    lateinit var inetAddress: InetAddress
    var port: Int = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init sensor listening
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
            SensorManager.SENSOR_DELAY_NORMAL
        )

        // init UI
        val ipInput = findViewById<EditText>(R.id.input_ip)
        val portInput = findViewById<EditText>(R.id.input_port)
        val button1 = findViewById<Button>(R.id.button_1)
        val button2 = findViewById<Button>(R.id.button_2)
        val button3 = findViewById<Button>(R.id.button_3)
        val button4 = findViewById<Button>(R.id.button_4)
        val sendingSwitch = findViewById<SwitchCompat>(R.id.switch_send)

        var stateButton1 = 0
        var stateButton2 = 0
        var stateButton3 = 0
        var stateButton4 = 0

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        val savedIP = sharedPref.getString("IP_address", "default")
        val savedPort = sharedPref.getInt("PORT", 0)

        if (!savedIP.equals("default") && (savedIP != null) && (savedPort != 0)) {
            ipAddress = savedIP
            port = savedPort
            ipInput.setText(savedIP)
            portInput.setText(savedPort.toString())

        }

        // init UI input listeners
        ipInput.doOnTextChanged { text, _, _, _ ->
            ipAddress = text.toString()

            with(sharedPref.edit()) {
                putString("IP_address", ipAddress)
                apply()
            }

        }
        portInput.doOnTextChanged { text, _, _, _ ->
            if (text != null) {
                port = Integer.parseInt(text.toString())
            }

            with(sharedPref.edit()) {
                putInt("PORT", port)
                apply()
            }
        }

        button1.setOnTouchListener { v, event ->
            // v.onTouchEvent(event)
            val stateView = findViewById<TextView>(R.id.value_button1)
            if (event.action == MotionEvent.ACTION_DOWN) {
                stateButton1 = 1
                stateView.text = "Button 1 - DOWN"
            }
            if (event.action == MotionEvent.ACTION_UP) {
                stateButton1 = 0
                stateView.text = "Button 1 - UP"
            }

            true
        }

        button2.setOnTouchListener { v, event ->
           // v.onTouchEvent(event)
            val stateView = findViewById<TextView>(R.id.value_button2)
            if (event.action == MotionEvent.ACTION_DOWN) {
                stateButton2 = 1
                stateView.text = "Button 2 - DOWN"
            }
            if (event.action == MotionEvent.ACTION_UP) {
                stateButton2 = 0
                stateView.text = "Button 2 - UP"
            }

            true
        }

        button3.setOnTouchListener { v, event ->
            // v.onTouchEvent(event)
            val stateView = findViewById<TextView>(R.id.value_button3)
            if (event.action == MotionEvent.ACTION_DOWN) {
                stateButton3 = 1
                stateView.text = "Button 3 - DOWN"
            }
            if (event.action == MotionEvent.ACTION_UP) {
                stateButton3 = 0
                stateView.text = "Button 3 - UP"
            }

            true
        }

        button4.setOnTouchListener { v, event ->
            // v.onTouchEvent(event)
            val stateView = findViewById<TextView>(R.id.value_button4)
            if (event.action == MotionEvent.ACTION_DOWN) {
                stateButton4 = 1
                stateView.text = "Button 4 - DOWN"
            }
            if (event.action == MotionEvent.ACTION_UP) {
                stateButton4 = 0
                stateView.text = "Button 4 - UP"
            }

            true
        }

        sendingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activateSending()
            } else {
                deactivateSending()
            }
        }

        // send button states every 1000ms
        val task = object : TimerTask() {
            override fun run() {
                if (sendingActive) {
                    val msg1 = "{\"button_1\":\"$stateButton1\"}"
                    lifecycleScope.launch {
                        sendData(msg1)
                    }
                    val msg2 = "{\"button_2\":\"$stateButton2\"}"
                    lifecycleScope.launch {
                        sendData(msg2)
                    }
                    val msg3 = "{\"button_3\":\"$stateButton3\"}"
                    lifecycleScope.launch {
                        sendData(msg3)
                    }
                    val msg4 = "{\"button_4\":\"$stateButton4\"}"
                    lifecycleScope.launch {
                        sendData(msg4)
                    }
                }
            }
        }
        val timer = Timer()
        timer.scheduleAtFixedRate(task, 0L, 1000)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val accValueView = findViewById<TextView>(R.id.value_accelerometer)
        val gyroValueView = findViewById<TextView>(R.id.value_gyroscope)
        val gravValueView = findViewById<TextView>(R.id.value_gravity)

        // accelerometer: Acceleration force along the x/y/z axis (including gravity). (m/s*s)
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val accX = event.values[0]
            val accY = event.values[1]
            val accZ = event.values[2]
            accValueView.text = getString(
                R.string.xyz_value,
                accX.toString(), accY.toString(), accZ.toString()
            )

            if (sendingActive) {
                val msg =
                    "{\"accelerometer\":{\"x\":$accX,\"y\":$accY,\"z\":$accZ}}"
                lifecycleScope.launch {
                    sendData(msg)
                }
            }
        }

        // gyroscope: Rate of rotation around the x/y/z axis. (rad/s)
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gyroX = event.values[0]
            val gyroY = event.values[1]
            val gyroZ = event.values[2]
            gyroValueView.text = getString(
                R.string.xyz_value,
                gyroX.toString(), gyroY.toString(), gyroZ.toString()
            )

            if (sendingActive) {
                val msg =
                    "{\"gyroscope\":{\"x\":$gyroX,\"y\":$gyroY,\"z\":$gyroZ}}"
                lifecycleScope.launch {
                    sendData(msg)
                }
            }
        }

        // gravity: Force of gravity along the x/y/z axis. (m/s*s)
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            val gravX = event.values[0]
            val gravY = event.values[1]
            val gravZ = event.values[2]
            gravValueView.text = getString(
                R.string.xyz_value,
                gravX.toString(), gravY.toString(), gravZ.toString()
            )

            if (sendingActive) {
                val msg =
                    "{\"gravity\":{\"x\":$gravX,\"y\":$gravY,\"z\":$gravZ}}"
                lifecycleScope.launch {
                    sendData(msg)
                }
            }
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i("INFO", "accuracy changed")
    }

    private fun activateSending() {
        if (ipAddress.matches(("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" + "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\$").toRegex()) &&
            port.toString()
                .matches(("^([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])\$").toRegex())
        ) {
            inetAddress = InetAddress.getByName(ipAddress)
            sendingActive = true
        } else {
            findViewById<SwitchCompat>(R.id.switch_send).isChecked = false
            Toast.makeText(this, "Invalid IP or port input", Toast.LENGTH_LONG).show()
        }
    }

    private fun deactivateSending() {
        sendingActive = false
    }

    private suspend fun sendData(msg: String) = withContext(Dispatchers.IO) {
        Log.i("MESSAGE TO SEND", msg)

        try {
            val datagramSocket = DatagramSocket().also {
                it.reuseAddress = true
                if (!it.isConnected) it.connect(inetAddress, port)
            }

            val bufData: ByteArray = msg.toByteArray()
            val dataPacket =
                DatagramPacket(bufData, bufData.size, inetAddress, port)

            datagramSocket.send(dataPacket)
            datagramSocket.close()

        } catch (e: Exception) {
            Log.e("SENDING", "An error occurred while sending data")
            Log.e("SENDING", "localized msg: " + e.localizedMessage)
            e.printStackTrace()
        }
    }

}