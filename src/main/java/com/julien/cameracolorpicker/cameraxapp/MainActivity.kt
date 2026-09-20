package com.julien.cameracolorpicker.cameraxapp

import android.content.ClipData
import android.content.ClipboardManager
import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.julien.cameracolorpicker.cameraxapp.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.get
import kotlin.text.toHexString


typealias pixelListener = (ImageBitmap : Bitmap) -> Unit

// https://developer.android.com/codelabs/camerax-getting-started?hl=fr
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding;
    private lateinit var cameraExecutor: ExecutorService;
    private val REQUEST_CODE_PERMISSIONS = 10;
    private val REQUIRED_PERMISSIONS = mutableListOf (Manifest.permission.CAMERA).toTypedArray();

    lateinit var colorView: View
    lateinit var textColorView: TextView
    lateinit var pixelSearchRadioGroup: RadioGroup
    var pixelSearchScope : Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(layoutInflater);
        setContentView(viewBinding.root);

        colorView = findViewById(R.id.viewColor);
        textColorView = findViewById(R.id.textColor);
        pixelSearchRadioGroup = findViewById(R.id.pixelsSearchRadioGroup);

        //https://www.geeksforgeeks.org/android/clipboard-in-android/
        // Initializing the ClipboardManager and Clip data
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        var clipData: ClipData
        textColorView.setOnClickListener {

            // clip data is initialized with the text variable declared above
            clipData = ClipData.newPlainText("text", textColorView.text)

            // Clipboard saves this clip object
            clipboardManager.setPrimaryClip(clipData)

            // A toast is shown for user reference that the text is copied to the clipboard
            Toast.makeText(applicationContext, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
        }

        pixelSearchRadioGroup.setOnCheckedChangeListener { radioGroup, i ->
            if( pixelSearchRadioGroup.checkedRadioButtonId != -1 ) {
                val selectedButton = findViewById<RadioButton>(pixelSearchRadioGroup.checkedRadioButtonId).contentDescription.toString()
                pixelSearchScope = selectedButton.toInt()
                //Log.i("msg", pixelSearchScope.toString());
            }
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private class PixelPicker(private val listener: pixelListener) : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {

            val bitmap = image.toBitmap()
            image.close()

            listener(bitmap)
        }
    }

    // main function
    private fun imageColorGet(imageBitmap : Bitmap) {

        // init vars
        var pixel : Int = 0
        var r : Int = 0
        var g : Int = 0
        var b : Int = 0

        // get the pixel according to the search scope
        if(pixelSearchScope == 1) {
            pixel = imageBitmap[imageBitmap.width / 2, imageBitmap.height / 2]
            r = Color.red(pixel);
            g = Color.green(pixel);
            b = Color.blue(pixel);
        }
        else {
            var pixelsSum = getMiddlePixelsSum(imageBitmap, pixelSearchScope)
            r = pixelsSum[0];
            g = pixelsSum[1];
            b = pixelsSum[2];
        }

        // update view and text
        val backgroundColor = Color.rgb(r,g,b)
        colorView.setBackgroundColor(backgroundColor);
        textColorView.text = getString(R.string.textColor, backgroundColor.toHexString().substring(2));
    }

    private fun getMiddlePixelsSum(imageBitmap : Bitmap, area : Int) :  Array<Int> {
        val middleWidth = imageBitmap.width / 2;
        val middleHeight = imageBitmap.height / 2;

        var pixelSum = Array<Int>(3) {0}; // r; g; b

        // for every pixel in the middle area of the screen and the area specified
        for(y in -(area/2)..(area/2)) {
            for(x in -(area/2)..(area/2)) {
                val pixel = imageBitmap[middleWidth+x, middleHeight+y];

                pixelSum[0] += Color.red(pixel);
                pixelSum[1] += Color.green(pixel);
                pixelSum[2] += Color.blue(pixel);
            }
        }

        val sumCount = area*area

        pixelSum[0] /= sumCount
        pixelSum[1] /= sumCount
        pixelSum[2] /= sumCount

        //Log.i("msg", "full:["+pixelSum[0].toString()+","+pixelSum[1].toString()+","+pixelSum[2].toString()+"]")

        return pixelSum
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get();

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                };

            val pixelsPicker = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, PixelPicker { image ->
                        //Log.i("msg", (bgColor).toHexString());
                        imageColorGet(image);
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, pixelsPicker);

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc);
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED;
    }

    override fun onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}