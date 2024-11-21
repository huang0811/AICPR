package lyi.linyi.posemon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import lyi.linyi.posemon.ml.MoveNet
import lyi.linyi.posemon.ml.ModelType
import lyi.linyi.posemon.data.Device
import lyi.linyi.posemon.data.BodyPart
import lyi.linyi.posemon.camera.CameraSource
import lyi.linyi.posemon.data.Camera

class chat_robot3 : AppCompatActivity() {
//
//    private val CAMERA_PERMISSION_CODE = 1001
//    private val messages = mutableListOf<ChatMessage>()
//    private lateinit var chatAdapter: ChatAdapter
//    private lateinit var surfaceView: SurfaceView
//
//    private var fastwarnPlayer: MediaPlayer? = null
//    private var slowwarnPlayer: MediaPlayer? = null
//    private var shallowPlayer: MediaPlayer? = null
//
//    // Pose detection related
//    private var cameraSource: CameraSource? = null
//    private var model = ModelType.Thunder
//    private var device = Device.CPU
//    private lateinit var tempBitmap: Bitmap  // 用於暫存影像的 Bitmap
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_chat_robot3)
//
//        fastwarnPlayer = MediaPlayer.create(this, R.raw.fastvoice)
//        slowwarnPlayer = MediaPlayer.create(this, R.raw.slowvoice)
//        shallowPlayer = MediaPlayer.create(this, R.raw.shallowvoice)
//
//        surfaceView = findViewById(R.id.surfaceView)
//        surfaceView.holder.addCallback(surfaceCallback)
//
//        val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
//        chatAdapter = ChatAdapter(messages)
//        chatRecyclerView.layoutManager = LinearLayoutManager(this)
//        chatRecyclerView.adapter = chatAdapter
//
//        val btHome = findViewById<ImageButton>(R.id.btHome)
//        btHome.setOnClickListener {
//            val intent = Intent(this, SelectActivity::class.java)
//            startActivity(intent)
//        }
//
//        val btResult = findViewById<ImageButton>(R.id.btResult)
//        btResult.setOnClickListener {
//            val intent = Intent(this, chat_robot4::class.java)
//            startActivity(intent)
//        }
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.CAMERA),
//                CAMERA_PERMISSION_CODE
//            )
//        } else {
//            openCamera()
//        }
//
//        CoroutineScope(Dispatchers.Main).launch {
//            setupPoseEstimation()
//        }
//    }
//
//    private suspend fun setupPoseEstimation() {
//        cameraSource = CameraSource(
//            surfaceView,
//            Camera.BACK,
//            object : CameraSource.CameraSourceListener {
//                override fun onFPSListener(fps: Int) {
//                    Log.d("PoseEstimation", "FPS: $fps")
//                }
//
//                override fun onDetectedInfo(
//                    personScore: Float?,
//                    poseLabels: List<Pair<String, Float>>?
//                ) {
//                    if (personScore != null && personScore > 0.3) {
//                        detectShouldersElbowsWrists()
//                    }
//                }
//            }).apply {
//            setDetector(MoveNet.create(this@chat_robot3, device, model))
//            initCamera()
//        }
//
//        // 初始化暫存影像的 Bitmap
//        tempBitmap = Bitmap.createBitmap(
//            surfaceView.width,
//            surfaceView.height,
//            Bitmap.Config.ARGB_8888
//        )
//    }
//
//    private fun detectShouldersElbowsWrists() {
//        cameraSource?.processImage(tempBitmap)  // 使用暫存的 Bitmap 進行影像處理
//        val keyPoints = cameraSource?.getDetector()?.estimatePoses(tempBitmap)?.getOrNull(0)?.keyPoints ?: return
//
//        val leftShoulder = keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }
//        val rightShoulder = keyPoints.find { it.bodyPart == BodyPart.RIGHT_SHOULDER }
//        val leftElbow = keyPoints.find { it.bodyPart == BodyPart.LEFT_ELBOW }
//        val rightElbow = keyPoints.find { it.bodyPart == BodyPart.RIGHT_ELBOW }
//        val leftWrist = keyPoints.find { it.bodyPart == BodyPart.LEFT_WRIST }
//        val rightWrist = keyPoints.find { it.bodyPart == BodyPart.RIGHT_WRIST }
//
//        Log.d("PoseEstimation", "Left Shoulder: ${leftShoulder?.coordinate}")
//        Log.d("PoseEstimation", "Right Shoulder: ${rightShoulder?.coordinate}")
//        Log.d("PoseEstimation", "Left Elbow: ${leftElbow?.coordinate}")
//        Log.d("PoseEstimation", "Right Elbow: ${rightElbow?.coordinate}")
//        Log.d("PoseEstimation", "Left Wrist: ${leftWrist?.coordinate}")
//        Log.d("PoseEstimation", "Right Wrist: ${rightWrist?.coordinate}")
//    }
//
//    private fun openCamera() {
//        cameraSource?.resume()
//    }
//
//    private val surfaceCallback = object : SurfaceHolder.Callback {
//        override fun surfaceCreated(holder: SurfaceHolder) {
//            openCamera()
//        }
//
//        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
//
//        override fun surfaceDestroyed(holder: SurfaceHolder) {
//            cameraSource?.close()
//            Log.d("SurfaceCallback", "Surface destroyed and resources released")
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == CAMERA_PERMISSION_CODE) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                openCamera()
//            } else {
//                Toast.makeText(this, "相機權限被拒絕", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        fastwarnPlayer?.release()
//        slowwarnPlayer?.release()
//        shallowPlayer?.release()
//        cameraSource?.close()
//    }
//
//    private fun addMessage(message: String, isUser: Boolean) {
//        messages.add(ChatMessage(message, isUser))
//        chatAdapter.notifyItemInserted(messages.size - 1)
//        findViewById<RecyclerView>(R.id.chatRecyclerView).scrollToPosition(messages.size - 1)
//    }
}