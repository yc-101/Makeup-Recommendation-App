package com.example.camera2

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
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
import kotlin.arrayOf
import kotlin.let


class MainActivity : Activity() {
    private var TAG: String = "test"
    private var mCameraId: String? = null
    private var mPreviewSize: Size? = null
    private var mCaptureSize: Size? = null
    private var mCameraThread: HandlerThread? = null
    private var mCameraHandler: Handler? = null
    private var mCameraDevice: CameraDevice? = null
    private var mTextureView: TextureView? = null
    private var mImageReader: ImageReader? = null
    private var mCaptureRequestBuilder: CaptureRequest.Builder? = null
    private var mCaptureRequest: CaptureRequest? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private var mImage2: Image? = null
    private var mFaceButton: Button? = null
    private var mGraphicOverlay: GraphicOverlay? = null
    private var isDetecting = false//相機物理角度
    private var mSensorOrientation: Int? = 0
    private var mRotationCompensation: Int? = 0
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
                runFaceContourDetection(mImage2!!)
            }
        })
        mTextureView = findViewById<View>(R.id.textureView) as TextureView
        mGraphicOverlay = findViewById(R.id.graphic_overlay)
    }
    private fun runFaceContourDetection(image: Image) {
        if(mImage2 == null) {
            Log.e(TAG, "FAIL TO detect")
            return
        }
        Log.d(TAG, "Start detect" + mRotationCompensation)
        val image: InputImage = InputImage.fromMediaImage(mImage2!!, 0) // rotation?
        val options: FaceDetectorOptions = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        mFaceButton!!.isEnabled = false
        val detector: FaceDetector = FaceDetection.getClient(options)
        detector.process(image)
            .addOnSuccessListener { faces ->
                mFaceButton!!.setEnabled(true);
                Log.d(TAG, "detecting........." + faces.size)
                processFaceContourDetectionResult(faces);

                // Task completed successfully
                // [START_EXCLUDE]
                // [START get_face_info]
                for (face in faces) {
                    val bounds = face.boundingBox
                    val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                    val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

                    Log.d(TAG, "rotY: "+rotY)
                    Log.d(TAG, "rotZ: "+rotZ)
                    // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                    // nose available):
                    val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                    leftEar?.let {
                        val leftEarPos = leftEar.position
                        Log.d(TAG, "leftEarPos: "+leftEarPos)
                    }

                    // If classification was enabled:
                    if (face.smilingProbability != null) {
                        val smileProb = face.smilingProbability
                        Log.d(TAG, "smile: "+smileProb)
                    }
                    if (face.rightEyeOpenProbability != null) {
                        val rightEyeOpenProb = face.rightEyeOpenProbability
                        Log.d(TAG, "rightEyeOpenProb: "+rightEyeOpenProb)
                    }

                    // If face tracking was enabled:
                    if (face.trackingId != null) {
                        val id = face.trackingId
                        Log.d(TAG, "id: "+id)
                    }
                }
                if(mImage2 != null) {
                    mImage2!!.close()
                    isDetecting = false
                    Log.d(TAG, "close image")
                }
                else {
                    Log.d(TAG, "close image FAIL!! (mImage2 is null)")
                }

                // [END get_face_info]
                // [END_EXCLUDE]
            }

            .addOnFailureListener { e ->
                // Task failed with an exception
                mFaceButton!!.isEnabled = true
                e.printStackTrace()
                Log.e(TAG, "FAIL FACE DETECTION")
            }
//        mImage2?.close()
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

        Log.d(TAG, "Contour.........finish")
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
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
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
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
                if (facing != null && facing == CameraCharacteristics.AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR)
                    continue

//                // Get the device's current rotation relative to its "native" orientation.
//                // Then, from the ORIENTATIONS table, look up the angle the image must be
//                // rotated to compensate for the device's rotation.
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

                for (size:Size in map.getOutputSizes(ImageFormat.JPEG)){
                    Log.i(TAG, "setUpCamera-->size:$size")
                }
                //获取相机支持的最大拍照尺寸
                mCaptureSize = Collections.max(map.getOutputSizes(ImageFormat.JPEG).asList()
                ) { o1, o2 -> o1!!.width * o1.height - o2!!.width * o2.height }
                //此ImageReader用于拍照所需
                setupImageReader()

                mCameraId = cameraId
                Log.d(TAG, "[$cameraId] facing : $facing")
                Log.d(TAG, "[$cameraId] ORIENTATION : "+mRotationCompensation)
                Log.i(TAG, "-->width:$width height:$height-->setUpCamera-->mPreviewSize:$mPreviewSize-->mCaptureSize:$mCaptureSize")

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
            if (width > height) {
                if (option.width > width && option.height > height) {
                    sizeList.add(option)
                }
            } else {
                if (option.width > height && option.height > width) {
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
        } else sizeMap[0]
    }

    private fun openCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        var permissions:Array<String> = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        var num = 0
        for(permission:String in permissions){
            if (ActivityCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(permissions, num++)
                return
            }
        }
        manager?.openCamera(mCameraId!!, mStateCallback, mCameraHandler)
        Log.d(TAG, "Camera [$mCameraId] opened.")
    }

    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            mCameraDevice = null
        }
    }

    private fun startPreview() {
        val mSurfaceTexture = mTextureView!!.surfaceTexture
        mTextureView!!.setRotation(90F)
        mSurfaceTexture!!.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
        val previewSurface = Surface(mSurfaceTexture)
        try {
            Log.d("test","start Preview")
            mCaptureRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mCaptureRequestBuilder!!.addTarget(previewSurface)
            mCaptureRequestBuilder!!.addTarget(mImageReader!!.surface)
            Log.d(TAG, "SIZE:" +mPreviewSize!!.width+" , "+ mPreviewSize!!.height)
            mCameraDevice!!.createCaptureSession(
                Arrays.asList(
                    previewSurface,
                    mImageReader!!.surface
                ), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
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

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, mCameraHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

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

//    private final ImageReader.OnImageAvailableListener  listener = new ImageReader.OnImageAvailableListener() {
//        @Override
//        public void onImageAvailable(ImageReader imageReader) {
//
//        }
//    };
    private fun setupImageReader() {
        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(
            mCaptureSize!!.width, mCaptureSize!!.height,
            ImageFormat. YUV_420_888, 2 // JPEG, 2 (YUV較有效率)
        )
        mImageReader!!.setOnImageAvailableListener({ reader ->
            Log.i("test", "detecting: "+ isDetecting)
            if(!isDetecting) {
                isDetecting = true
                mImage2 = reader.acquireNextImage()
                if (mImage2 != null) {
                    Log.i("test", "next img")
                    runFaceContourDetection(mImage2!!)
//                    mImage2?.close()
//                    isDetecting = false
                }

//            var inputStream: InputStream? = null
//            var mSelectedImage: Bitmap? = null
//            try {
//                inputStream = assets.open("grace_hopper.jpg")
//                mSelectedImage = BitmapFactory.decodeStream(inputStream)
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//            if(mSelectedImage != null) {
//                // Get the dimensions of the View
//                var targetedSize: Pair<Integer, Integer> = getTargetedWidthHeight();
//
//                int targetWidth = targetedSize.first;
//                int maxHeight = targetedSize.second;
//
//                // Determine how much to scale down the image
//                float scaleFactor =
//                Math.max(
//                    (float) mSelectedImage.getWidth() / (float) targetWidth,
//                    (float) mSelectedImage.getHeight() / (float) maxHeight);
//
//                Bitmap resizedBitmap =
//                Bitmap.createScaledBitmap(
//                    mSelectedImage,
//                    (int) (mSelectedImage.getWidth() / scaleFactor),
//                    (int) (mSelectedImage.getHeight() / scaleFactor),
//                    true);
//
//                mImageView.setImageBitmap(resizedBitmap);
//                mSelectedImage = resizedBitmap;
//            }



//            mCameraHandler!!.post(
//                test
//                ImageSaver(
//                    reader.acquireNextImage()
//                )
//            )
            }
            else {
                Log.d(TAG, "dump image")
            }
        }, mCameraHandler)
    }

    class ImageSaver(private val mImage: Image) : Runnable {
        override fun run() {
            val buffer = mImage.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer[data]
            val path = Environment.getExternalStorageDirectory().toString() + "/DCIM/CameraV2/"
            val mImageFile = File(path)
            if (!mImageFile.exists()) {
                mImageFile.mkdir()
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = path + "IMG_" + timeStamp + ".jpg"
            Log.d("test", "image saved"+fileName)
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(fileName)
                fos.write(data, 0, data.size)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (fos != null) {
                    try {
                        fos.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

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
}
