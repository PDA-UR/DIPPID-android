package com.android.dippid

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

class CameraFragment : Fragment(R.layout.fragment_camera),
    CameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        private var REQUEST_CODE_CAMERA_PERMISSION = 101
    }

    private var listener: DataListener? = null

    private lateinit var rgb: Mat
    private lateinit var gray: Mat

    private lateinit var ids: MatOfInt
    private lateinit var corners: MutableList<Mat>
    private lateinit var dictionary: Dictionary
    private lateinit var parameters: DetectorParameters

    private var openCvCameraView: CameraBridgeViewBase? = null

    private val loaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(activity) {
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            context is DataListener -> context
            parentFragment is DataListener -> parentFragment as DataListener
            else -> error("You should implement MyFragmentListener")

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        openCvCameraView = view.findViewById<org.opencv.android.JavaCameraView>(R.id.camera_view)

        // check for camera permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                context?.let {
                    ContextCompat.checkSelfPermission(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == PackageManager.PERMISSION_GRANTED -> {
                    // camera permission granted
                    activateOpenCVCameraView()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    // explain why the app needs this permission
                    val builder: AlertDialog.Builder? = this.let {
                        context?.let { it1 -> AlertDialog.Builder(it1) }
                    }
                    builder?.setMessage("Camera permission is needed to scan aruco markers.")
                        ?.setPositiveButton("Ok") { dialog, _ ->
                            dialog.dismiss()
                        }
                    val dialog: AlertDialog? = builder?.create()
                    dialog?.show()
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
                    context, "Camera permission required.",
                    Toast.LENGTH_LONG
                ).show()
                listener?.onCameraPermissionDenied()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onResume() {
        super.onResume()

        if (OpenCVLoader.initDebug()) {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            Toast.makeText(context, "OpenCV loaded", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "OpenCV loading failed", Toast.LENGTH_LONG).show()
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

            Log.i("multiple marker", "are there multiple markers?! -> ${corners.size}")

            val id = ids.toList().first()
            val height = gray.rows()
            val width = gray.cols()
            val topLeft = normalizeCoordinates(corners[0].get(0, 0), width, height)
            val topRight = normalizeCoordinates(corners[0].get(0, 1), width, height)
            val bottomLeft = normalizeCoordinates(corners[0].get(0, 2), width, height)
            val bottomRight = normalizeCoordinates(corners[0].get(0, 3), width, height)

            val message =
                "{\"marker\": {\"id\": $id," +
                        " \"top_left\": {\"x\": ${topLeft[0]}, \"y\": ${topLeft[1]}}," +
                        " \"top_right\": {\"x\": ${topRight[0]}, \"y\": ${topRight[1]}}," +
                        " \"bottom_right\": {\"x\": ${bottomRight[0]}, \"y\": ${bottomRight[1]}}," +
                        " \"bottom_left\": {\"x\": ${bottomLeft[0]}, \"y\": ${bottomLeft[1]}}}}"
            Log.i("DATA", "message: $message")

            listener?.onDataToSend(message)
        }

        return rgb
    }

    private fun normalizeCoordinates(array: DoubleArray, width: Int, height: Int): DoubleArray {
        val res = DoubleArray(2)
        res[0] = array[0] / width
        res[1] = array[1] / height
        return res
    }

}