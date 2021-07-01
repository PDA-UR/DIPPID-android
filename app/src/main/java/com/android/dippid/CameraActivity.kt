package com.android.dippid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.aruco.Aruco
import org.opencv.aruco.DetectorParameters
import org.opencv.aruco.Dictionary
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.imgproc.Imgproc
import java.util.*

class CameraActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        private var REQUEST_CODE_CAMERA_PERMISSION = 101
    }

    private lateinit var rgb: Mat
    private lateinit var gray: Mat

    private lateinit var ids: MatOfInt
    private lateinit var corners: MutableList<Mat>
    private lateinit var dictionary: Dictionary
    private lateinit var parameters: DetectorParameters

    private var openCvCameraView: CameraBridgeViewBase? = null

    private val loaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i("Status", "OpenCV loaded successfully")
                    openCvCameraView?.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setContentView(R.layout.activity_camera)

        openCvCameraView = findViewById<org.opencv.android.JavaCameraView>(R.id.camera_view)

        // check for camera permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // camera permission granted
                    activateOpenCVCameraView()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    // explain why the app needs this permission
                    val builder: AlertDialog.Builder = this.let {
                        AlertDialog.Builder(it)
                    }
                    builder.setMessage("Camera permission is needed to scan aruco markers.")
                        .setPositiveButton("Ok") { dialog, _ ->
                            dialog.dismiss()
                        }
                    val dialog: AlertDialog = builder.create()
                    dialog.show()
                }
                else -> {
                    // directly ask for the permission.
                    requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CODE_CAMERA_PERMISSION
                    )
                }
            }
        }
    }

    private fun activateOpenCVCameraView() {
        openCvCameraView?.setCameraPermissionGranted()
        openCvCameraView?.visibility = SurfaceView.VISIBLE
        openCvCameraView?.setCvCameraViewListener(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {

        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // The request was granted -> tell the camera view
                activateOpenCVCameraView()
            } else {
                // The request was denied -> tell the user and exit the activity
                Toast.makeText(
                    this, "Camera permission required.",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onResume() {
        super.onResume()

        if (OpenCVLoader.initDebug()) {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            Toast.makeText(this, "OpenCV loaded", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "OpenCV loading failed", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        openCvCameraView?.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        openCvCameraView?.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        rgb = Mat()
        corners = LinkedList()
        parameters = DetectorParameters.create()
        dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_50)
    }

    override fun onCameraViewStopped() {
        rgb.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        Imgproc.cvtColor(inputFrame.rgba(), rgb, Imgproc.COLOR_RGBA2RGB)
        gray = inputFrame.gray()

        ids = MatOfInt()
        corners.clear()

        Aruco.detectMarkers(gray, dictionary, corners, ids, parameters)

        if (corners.size > 0) {
            Aruco.drawDetectedMarkers(rgb, corners, ids)
            Log.i(
                "DATA",
                "corner values: ${corners[0].dump()} | id: ${
                    ids.toArray()?.contentToString()
                }"
            )
            // todo: send data
        }

        return rgb
    }

}