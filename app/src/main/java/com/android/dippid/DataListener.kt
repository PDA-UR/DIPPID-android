package com.android.dippid

import java.net.InetAddress

interface DataListener {

    fun onDataToSend(messageToSend: String)

    fun onSendingActivated(portInput: Int, addressInput: InetAddress)

    fun onSendingDeactivated()

    fun onCameraPermissionDenied()

}