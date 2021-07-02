package com.android.dippid

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity(R.layout.dippid_activity), DataListener {

    private var inetAddress: InetAddress? = null
    private var port: Int = 0
    var sendingActive = false
    var selectedTab: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<DebugFragment>(R.id.fragment_container_view)
            }
        } else {
            try {
                sendingActive = savedInstanceState.getBoolean("sending_state")
                inetAddress = InetAddress.getByAddress(savedInstanceState.getByteArray("address"))
                port = savedInstanceState.getInt("port")
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }

            val savedTab = savedInstanceState.getInt("selected_tab")
            val tab = tabLayout.getTabAt(savedTab)
            tab?.select()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        selectedTab = 0
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DebugFragment>(R.id.fragment_container_view)
                        }
                    }
                    1 -> {
                        selectedTab = 1
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<MarkerFragment>(R.id.fragment_container_view)
                        }
                    }
                    2 -> {
                        selectedTab = 2
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<CameraFragment>(R.id.fragment_container_view)
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_tab", selectedTab)
        outState.putBoolean("sending_state", sendingActive)
        if (inetAddress != null) {
            outState.putByteArray("address", inetAddress?.address)
        }
        outState.putInt("port", port)
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

    override fun onDataToSend(messageToSend: String) {
        if (sendingActive) {
            lifecycleScope.launch {
                sendData(messageToSend)
            }
        }
    }

    override fun onSendingActivated(portInput: Int, addressInput: InetAddress) {
        port = portInput
        inetAddress = addressInput
        sendingActive = true
    }

    override fun onSendingDeactivated() {
        sendingActive = false
    }

    override fun onCameraPermissionDenied() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace<DebugFragment>(R.id.fragment_container_view)
        }
    }

    fun isSendingActive(): Boolean {
        return sendingActive
    }

}