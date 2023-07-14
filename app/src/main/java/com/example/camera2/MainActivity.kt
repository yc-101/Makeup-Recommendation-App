package com.example.camera2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.OutputConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Long
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Comparator
import kotlin.Int
import kotlin.String
import kotlin.apply
import kotlin.arrayOf
import kotlin.collections.ArrayList
import kotlin.let


class MainActivity : Activity() {
    private var TAG: String = "test"
    private var mCameraId: String? = null
    private var mPreviewSize: Size? = null
    private var mCaptureSize: Size? = null
    private var mCameraThread: HandlerThread? = null
    private var mCameraHandler: Handler? = null
    private var mCameraDevice: CameraDevice? = null
    private var mImageReader: ImageReader? = null
    private var mCaptureRequestBuilder: CaptureRequest.Builder? = null
    private var mCaptureRequest: CaptureRequest? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private var mImage2: Image? = null
    private var isDetecting = false
    private var mSensorOrientation: Int? = 0
    private var mRotationCompensation: Int? = 0
    // ================== UI ================== //
    private var mTextureView: TextureView? = null
    private var mFaceButton: Button? = null
    private var mGraphicOverlay: GraphicOverlay? = null
    private var mTempImageView: ImageView? = null
    private var mCloseButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        //全屏无状态栏
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_FULLSCREEN,
//            WindowManager.LayoutParams.FLAG_FULLSCREEN
//        )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        mFaceButton = findViewById(R.id.button_face);
        mFaceButton?.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
                mFaceButton!!.setEnabled(false)
            }
        })
        mTextureView = findViewById<View>(R.id.textureView) as TextureView
        mGraphicOverlay = findViewById(R.id.graphic_overlay)
        mTempImageView = findViewById(R.id.photoImageView)
        mCloseButton = findViewById(R.id.closeButton)
        mCloseButton?.setOnClickListener {
            mTempImageView?.visibility = View.GONE
            mCloseButton?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        startCameraThread()
        if (!mTextureView!!.isAvailable) {
            mTextureView!!.surfaceTextureListener = mTextureListener
        } else {
            startPreview()
        }
    }

    private fun startCameraThread() {
        mCameraThread = HandlerThread("CameraThread")
        mCameraThread!!.start()
        mCameraHandler = Handler(mCameraThread!!.looper)
    }

    private val mTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            //当SurefaceTexture可用的时候，设置相机参数并打开相机
            setupCamera(width, height)
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.i(TAG, "onSurfaceTextureSizeChanged")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.i(TAG, "onSurfaceTextureDestroyed")
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//            Log.i(TAG, "onSurfaceTextureUpdated")
        }
    }

    private fun setupCamera(width: Int, height: Int) {
        //获取摄像头的管理者CameraManager
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            //遍历所有摄像头
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
//                mSensorOrientation = 0
                //要取前鏡頭 => 若拿到後鏡頭，則不做動作
                if (facing != null && cameraId != "/dev/video3")
                    continue

                // Get the device's current rotation relative to its "native" orientation.
                // Then, from the ORIENTATIONS table, look up the angle the image must be
                // rotated to compensate for the device's rotation.
                val deviceRotation = windowManager.defaultDisplay.rotation
                mRotationCompensation = DEFAULT_ORIENTATIONS.get(deviceRotation)
                if (facing == 1) {
                    mRotationCompensation = (mSensorOrientation!! + mRotationCompensation!!) % 360
                } else { // back-facing
                    mRotationCompensation = (mSensorOrientation!! - mRotationCompensation!! + 360) % 360
                }
                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                val map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                //根据TextureView的尺寸设置预览尺寸
                mPreviewSize =
                    getOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height)

//                configureTextureViewTransform(mTextureView!!.getWidth(),mTextureView!!.getHeight())
                //获取相机支持的最大拍照尺寸
//                mCaptureSize = Collections.max(map.getOutputSizes(ImageFormat.JPEG).asList()
//                ) { o1, o2 -> o1!!.width * o1.height - o2!!.width * o2.height }
                mCaptureSize = getOptimalSize(map.getOutputSizes(ImageFormat.JPEG), width, height)
                //此ImageReader用于拍照所需
                setupImageReader()

                mCameraId = cameraId
                Log.d(TAG, "[$cameraId] facing : $facing")
                Log.d(TAG, "[$cameraId] ORIENTATION : "+mRotationCompensation)
                Log.i(TAG, "screen:$width x$height , setUpCamera , mPreviewSize:$mPreviewSize , mCaptureSize:$mCaptureSize")

                break
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    //选择sizeMap中大于并且最接近width和height的size
    private fun getOptimalSize(sizeMap: Array<Size>, width: Int, height: Int): Size {
        val sizeList: MutableList<Size> = ArrayList()
        for (option in sizeMap) {
            Log.i(TAG, option.toString())
            if (width > height) {
                if (option.width > width && option.height > height) {
                    Log.i(TAG, "add option w,h = "+option.width+","+option.height)
                    sizeList.add(option)
                }
            } else {
                if (option.width > height && option.height > width) {
                    Log.i(TAG, "add option w,h = "+option.width+","+option.height)
                    sizeList.add(option)
                }
            }
        }
        return if (sizeList.size > 0) {
            Collections.min(sizeList, object : Comparator<Size?> {
                override fun compare(p0: Size?, p1: Size?): Int {
                    return Long.signum((p0!!.width * p0.height - p1!!.width * p1.height).toLong())
                }
            })
        } else {
//            return sizeMap.last()
            return sizeMap[2] // 0
        }
    }

    private fun openCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        var permissions:Array<String> = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        var num = 0
        for(permission:String in permissions){
            if (ActivityCompat.checkSelfPermission(this, permission ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, num++)
                return
            }
        }
        manager.openCamera(mCameraId!!, mStateCallback, mCameraHandler)
        Log.d(TAG, "Camera [$mCameraId] opened.")
    }

    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "onOpened")
            mCameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "onDisconnected")
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "onError")
            camera.close()
            mCameraDevice = null
        }
    }

    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "CameraCaptureSession : onConfigured")
            try {
                mCaptureRequest = mCaptureRequestBuilder!!.build()
                mCameraCaptureSession = session
                mCameraCaptureSession!!.setRepeatingRequest(
                    mCaptureRequest!!,
                    null,
                    mCameraHandler
                )
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.d(TAG, "CameraCaptureSession : onConfigureFailed")
        }
    }

    private fun startPreview() {
        val mSurfaceTexture = mTextureView!!.surfaceTexture
        mSurfaceTexture!!.setDefaultBufferSize(mPreviewSize!!.height, mPreviewSize!!.width)

        val previewSurface = Surface(mSurfaceTexture)
        try {
            Log.d("test","start Preview")
            mCaptureRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mCaptureRequestBuilder!!.addTarget(previewSurface)
            mCaptureRequestBuilder!!.addTarget(mImageReader!!.surface)
            Log.d(TAG, "preview (w,h):" +mPreviewSize!!.width+" , "+ mPreviewSize!!.height)
            val surfaces = mutableListOf<Surface>()
            surfaces.add(previewSurface)
            surfaces.add(mImageReader!!.surface)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigurations = mutableListOf<OutputConfiguration>()
                for (surface in surfaces) {
                    outputConfigurations.add(OutputConfiguration(surface))
                }
                mCameraDevice!!.createCaptureSessionByOutputConfigurations(
                    outputConfigurations,
                    mSessionCallback,
                    mCameraHandler
                )
            } else {
                mCameraDevice!!.createCaptureSession(surfaces, mSessionCallback, mCameraHandler)
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val previewWidth = mPreviewSize!!.width
        val previewHeight = mPreviewSize!!.height

        val viewWidth = mTextureView!!.width
        val viewHeight = mTextureView!!.height

        val scaleX = viewWidth.toFloat() / previewWidth
        val scaleY = viewHeight.toFloat() / previewHeight
Log.d(TAG, "scaleX: "+scaleX)
        val scaledWidth: Int
        val scaledHeight: Int
        val xOffset: Int
        val yOffset: Int

        if (scaleX > scaleY) {
            scaledWidth = (previewWidth * scaleY).toInt()
            scaledHeight = viewHeight
            xOffset = (viewWidth - scaledWidth) / 2
            yOffset = 0
        } else {
            scaledWidth = viewWidth
            scaledHeight = (previewHeight * scaleX).toInt()
            xOffset = 0
            yOffset = (viewHeight - scaledHeight) / 2
        }
        runOnUiThread {
            mTextureView?.apply {
                layoutParams.width = scaledWidth
                layoutParams.height = scaledHeight
//                marginLeft = xOffset
//                marginTop = yOffset
//                layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
//                    setMargins(xOffset, yOffset, right, bottom)
//                }
                rotation = mRotationCompensation!! + 90F
                requestLayout()
            }
            mGraphicOverlay?.apply {
                layoutParams.width = scaledHeight // scaledWidth // mPreviewSize?.width ?: 0
                layoutParams.height = scaledWidth // scaledHeight //  mPreviewSize?.height ?: 0
//                marginLeft = xOffset
//                marginTop = yOffset
                rotation = mRotationCompensation!! + 0F
                requestLayout()
                Log.d(TAG, "overlay (w,h)"+layoutParams.width+", "+layoutParams.height)
            }
            mTempImageView?.apply {
                rotation = mRotationCompensation!! + 90F
                requestLayout()
            }
        }

    }

    override fun onPause() {
        super.onPause()
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession!!.close()
            mCameraCaptureSession = null
        }
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
        if (mImageReader != null) {
            mImageReader!!.close()
            mImageReader = null
        }
    }

    private fun setupImageReader() {
        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(
            mCaptureSize!!.width, mCaptureSize!!.height,
            ImageFormat.YUV_420_888, 2 // JPEG, 2 (YUV較有效率) // YUV_420_888, 2
        )
        mImageReader!!.setOnImageAvailableListener({ reader ->
//            Log.i("test", "detecting: "+ isDetecting)
            if(!isDetecting) {
                isDetecting = true
                mImage2 = reader.acquireLatestImage()
                if (mImage2 != null) {
//                    Log.i("test", "next img"+", width="+ mImage2?.width + ", height="+mImage2?.height)
                    runFaceContourDetection(mImage2!!)
                }
            }
            else {
//                Log.d(TAG, "dump image")
            }
        }, mCameraHandler)
    }

    /** Rotation / showToast **/
    companion object {
        val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90  // 後鏡頭
        val SENSOR_ORIENTATION_INVERSE_DEGREES = 270 // 前鏡頭

        val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 0)
            append(Surface.ROTATION_90, 90)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }

        val INVERSE_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    /** CAPTURE **/
    fun takePicture(view: View?) {
        lockFocus()
    }

    private fun lockFocus() {
        try {
            mCaptureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            mCameraCaptureSession!!.capture(
                mCaptureRequestBuilder!!.build(),
                mCaptureCallback,
                mCameraHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val mCaptureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            capture()
        }
    }

    private fun capture() {
        try {
            val mCaptureBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            val rotation = windowManager.defaultDisplay.rotation
            // mCaptureBuilder.addTarget(mImageReader!!.surface)
            mCaptureBuilder.set(
                CaptureRequest.JPEG_ORIENTATION,
                DEFAULT_ORIENTATIONS[rotation] // 後鏡頭旋轉
            )
            val CaptureCallback: CaptureCallback = object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Toast.makeText(applicationContext, "Image Saved!", Toast.LENGTH_SHORT).show()
                    unLockFocus()
                }
            }
            mCameraCaptureSession!!.stopRepeating()
            mCameraCaptureSession!!.capture(mCaptureBuilder.build(), CaptureCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun unLockFocus() {
        try {
            mCaptureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            //mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), null, mCameraHandler);
            mCameraCaptureSession!!.setRepeatingRequest(mCaptureRequest!!, null, mCameraHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /** SAVE IMAGE **/
    private fun saveBitmapAsImage(context: Context, bitmap: Bitmap) {
        Log.d("TEST2", "saveBitmapAsImage")
//        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
//        val fileName = "IMG_$timeStamp.jpg"
//
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraV2")
//        }
//
//        val resolver: ContentResolver = context.contentResolver
//        var outputStream: OutputStream? = null
//        var uri: Uri? = null
//
//        try {
//            val contentUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//            uri = resolver.insert(contentUri, contentValues)
//            if (uri == null) {
//                throw IOException("Failed to create image file")
//            }
//
//            outputStream = resolver.openOutputStream(uri)
//            if (outputStream == null) {
//                throw IOException("Failed to open output stream")
//            }
//
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
//            outputStream.flush()
//            Log.d(TAG, "Bitmap saved as image: ${uri.toString()}")
//        } catch (e: IOException) {
//            Log.e(TAG, "Failed to save bitmap as image: ${e.message}")
//        } finally {
//            try {
//                outputStream?.close()
//            } catch (e: IOException) {
//                Log.e(TAG, "Failed to close output stream: ${e.message}")
//            }
//        }

        // 显示照片到ImageView
        mTempImageView?.setImageBitmap(bitmap)
        mTempImageView?.visibility = View.VISIBLE
        mCloseButton?.visibility = View.VISIBLE
    }

    /** FACE DETECTOR **/
    private fun runFaceContourDetection(image: Image) {
        if(image == null) {
            Log.e(TAG, "FAIL TO detect")
            return
        }
//        Log.d(TAG, "Start detect" + mRotationCompensation)

        val inputImage: InputImage = InputImage.fromMediaImage(image, 90) // rotation?
        val options: FaceDetectorOptions = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        val detector: FaceDetector = FaceDetection.getClient(options)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (!mFaceButton!!.isEnabled) {
                    for (face in faces) {
                        Log.e(TAG, "image: " +inputImage.width +"x"+inputImage.height+
                                ",  face = [左${face.boundingBox.left}~右${face.boundingBox.right}, 上${face.boundingBox.top}~下${face.boundingBox.bottom}]" +
                                ", ${face.boundingBox.width()}x${face.boundingBox.height()}")

                        //The camera image received is in YUV YCbCr Format. Get buffers for each of the planes and use them to create a new bytearray defined by the size of all three buffers combined
                        val cameraPlaneY = inputImage.planes?.get(0)!!.buffer!!
                        val cameraPlaneV = inputImage.planes?.get(2)!!.buffer!!

                        val compositeByteArray = ByteArray(cameraPlaneY.capacity() + cameraPlaneV.capacity())

                        cameraPlaneY.get(compositeByteArray, 0, cameraPlaneY.capacity())
                        cameraPlaneV.get(compositeByteArray, cameraPlaneY.capacity(), cameraPlaneV.capacity())

                        val baOutputStream = ByteArrayOutputStream()
                        val yuvImage: YuvImage = YuvImage(compositeByteArray, ImageFormat.NV21, inputImage.width, inputImage.height, null)
                        val rect = Rect(
                            if (face.boundingBox.top > 0 )                      face.boundingBox.top else 0,
                            if (face.boundingBox.right < inputImage.height )    inputImage.height - face.boundingBox.right else 0,
                            if (face.boundingBox.bottom < inputImage.width )    face.boundingBox.bottom else inputImage.width,
                            if (face.boundingBox.left > 0 )                     inputImage.height - face.boundingBox.left else inputImage.height,
                        )
                        yuvImage.compressToJpeg(rect, 75, baOutputStream)
                        Log.d(TAG, "Rect: "+rect)
                        val byteForBitmap = baOutputStream.toByteArray()
                        val bitmapImage = BitmapFactory.decodeByteArray(byteForBitmap, 0, byteForBitmap.size)
                        if (bitmapImage != null) { // Check if the bitmap is not null
                            Log.d(TAG,
                                ", bitmap: "+bitmapImage.width+"x"+bitmapImage.height)
                            saveBitmapAsImage(this, bitmapImage)
                        } else {
                            Log.e(TAG, "Failed to decode bitmap from byte array")
                        }
                    }
                    mFaceButton!!.isEnabled = true
                }

//                Log.d(TAG, "detecting........." + faces.size+", (w,h)=("+inputImage.width+", "+inputImage.height+")")
                processFaceContourDetectionResult(faces);

                if(image != null) {
                    image.close()
                    isDetecting = false
//                    Log.d(TAG, "close image")
                }
                else {
                    Log.d(TAG, "close image FAIL!! (mImage2 is null)")
                }
            }

            .addOnFailureListener { e ->
                // Task failed with an exception
                e.printStackTrace()
                Log.e(TAG, "FAIL FACE DETECTION")
            }
    }

    private fun processFaceContourDetectionResult(faces: List<Face>) {
        // Task completed successfully
        if (faces.size == 0) {
//            showToast("No face found")
            return
        }
        mGraphicOverlay?.clear()
        for (i in faces.indices) {
            val face = faces[i]
            val faceGraphic = FaceContourGraphic(mGraphicOverlay)
            mGraphicOverlay?.add(faceGraphic)   // 畫偵測到的臉
            faceGraphic.updateFace(face)
        }

//        Log.d(TAG, "Contour.........finish")
    }
}
