/* Copyright 2022 Lin Yi. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

/** 本应用主要对 Tensorflow Lite Pose Estimation 示例项目的 MainActivity.kt
 *  文件进行了重写，示例项目中其余文件除了包名调整外基本无改动，原版权归
 *  The Tensorflow Authors 所有 */

package lyi.linyi.posemon

//import lyi.linyi.posemon.ml.PoseClassifier
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerLibraryInfo.TAG
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lyi.linyi.posemon.camera.CameraSource
import lyi.linyi.posemon.data.Camera
import lyi.linyi.posemon.data.Device
import lyi.linyi.posemon.ml.ModelType
import lyi.linyi.posemon.ml.MoveNet
import lyi.linyi.posemon.ml.PoseClassifier
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.sql.Types.NULL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.timer

data class CsvData(val deep: Float, val frequency: Int, val leftAngle: Int, val rightAngle: Int)
data class MaxDiffData(
    val deep: Float,
    val frequency: Float,
    val bothAngle: Float,
    val isCycleCompleted: Boolean
) : Parcelable {
    // 實現 Parcelable 接口的必要方法

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeFloat(deep)
        dest.writeFloat(frequency)
        dest.writeFloat(bothAngle)
        dest.writeByte(if (isCycleCompleted) 1 else 0)
    }

    companion object CREATOR : Parcelable.Creator<MaxDiffData> {
        override fun createFromParcel(parcel: Parcel): MaxDiffData {
            return MaxDiffData(parcel)
        }

        override fun newArray(size: Int): Array<MaxDiffData?> {
            return arrayOfNulls(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        parcel.readFloat(),
        parcel.readFloat(),
//        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readByte() != 0.toByte()
    )
}

class MainActivity : AppCompatActivity(), Player.Listener {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
        const val REQUEST_VIDEO_FILE = 1
        private const val REQUEST_CODE_MEDIA = 1001
    }

    /** 为视频画面创建一个 SurfaceView */
    private lateinit var surfaceView: SurfaceView

    /** 修改默认计算设备：CPU、GPU、NNAPI（AI加速器） */
    private var device = Device.NNAPI

    /** 修改默认摄像头：FRONT、BACK */
    private var selectedCamera = Camera.BACK

    private var model = ModelType.Thunder

    /** 定义几个计数器 */

    private var missingCounter = 0

    private var L165Counter = 0
    private var R165Counter = 0
    private var TGreater165Counter = 0
    private var TLess165Counter = 0

    val csvsave = false  //是否最後要存csv檔
    val windowSize = 2
    val timeDiffs = ArrayDeque<Int>(windowSize)
    val wristYDiffs = ArrayDeque<Float>(windowSize)
    val dataArrayList = ArrayList<CsvData>()
    val dataArrayList_3 = ArrayList<CsvData>()
    val timer = Timer()
    var prefrequency = 0
    var predeep = 0.0f
    var count = 0
    var count2 = 0
    var count3 = 0
    var flagwait = false
    var preTime = System.currentTimeMillis()
    var cycleidx = 0
    var cprflag = true
    var cprcount = 0
    var uncprcount = 0
    val maxDiffDataList = ArrayList<MaxDiffData>()
    private var willCycle = false
    private var totalDeep = 0.0
    private var totalFrequency = 0
    private var totalLeftAngle = 0
    private var totalRightAngle = 0
    private var totalBothAngle = 0
    private var totalcount = 0
    private var frequencyCount = 0
    private var deepCount = 0
    private var leftAngleCount = 0
    private var rightAngleCount = 0


    private lateinit var bufferedWriter: BufferedWriter//建立讀csv檔的緩衝全局變數
    private val dateFormat = SimpleDateFormat("MMddHHmmss") //自訂日期格式
    private val timestamp = dateFormat.format(Date())//抓時間


    /**定義一個歷史姿態寄存器*/
    private var poseRegister = "standard"

    private lateinit var btResult: ImageButton
    private lateinit var btHone: ImageButton

    private lateinit var tvTimer: TextView
    private var seconds = 0
    private val handler = Handler()
    private lateinit var runnable: Runnable

    private lateinit var tvFPS: TextView
    private lateinit var tvCycle: TextView
    private lateinit var tvDeep: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvAngle: TextView
    private lateinit var getContent: ActivityResultLauncher<Intent>
    private lateinit var player: ExoPlayer
    var beatPlayer: MediaPlayer? = null

    val concatenatingMediaSource = ConcatenatingMediaSource()
    private lateinit var dataSourceFactory: DataSource.Factory

    private var cameraSource: CameraSource? = null

    private var isClassifyPose = true


    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                /**得到用戶相機授權後，程序開始運行 */
                openCamera()
            } else {
                /** 提示用户“未獲得相機權限制，應用無法運行” */
                ErrorDialog.newInstance(getString(R.string.tfe_pe_request_permission))
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
            }
        }
    private fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33 及以上版本，請求具體媒體類型的權限
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                ),
                REQUEST_CODE_MEDIA
            )
        } else {
            // 傳統方式請求讀取外部存儲的權限
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_MEDIA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_MEDIA -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // 權限被授予，可以繼續進行 CSV 讀取或寫入
                    csvread() // 或 writeToCsv()
                } else {
                    // 權限被拒絕，顯示提示
                    Toast.makeText(this, "需要讀取媒體文件的權限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var changeDeviceListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            changeDevice(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            /** 如果用户未選擇運算設備，使用默認設備進行計算*/
        }
    }

    private var changeCameraListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, view: View?, direction: Int, id: Long) {
            changeCamera(direction)
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            /**如果使用用戶未選擇攝像頭，使用默認攝像頭進行拍攝 */
        }
    }

    private var changeModelListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, view: View?, direction: Int, id: Long) {
            changeModel(direction)
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            /**如果使用用戶未選擇攝像頭，使用默認模型來預測 */
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /**程序運行時保持畫面常亮 */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        btResult = findViewById(R.id.btResult)
        btHone = findViewById(R.id.btHome)

        tvTimer = findViewById(R.id.tvTimer)

        // 創建一個執行緒來處理計時
        runnable = object : Runnable {
            override fun run() {
                updateTimer()
                handler.postDelayed(this, 1000) // 每秒執行一次
            }
        }

        // 開始計時
        handler.post(runnable)

        tvCycle = findViewById(R.id.tvCycle)
        /** 用来顯示 按壓深度 信息 */
        tvDeep = findViewById(R.id.tvDeep)
        /** 用来顯示 按壓頻率 信息 */
        tvFrequency = findViewById(R.id.tvFrequency)
        /** 用来顯示 角度 信息 */
        tvAngle = findViewById(R.id.tvAngle)
//        maxDiffDataList.add(
//            MaxDiffData(
//                3.5F,
//                121,
//                165,
//                168
//            )
//        )


        /**用來顯示當前坐姿狀態 */
        //ivStatus = findViewById(R.id.ivStatus)

        tvFPS = findViewById(R.id.tvFps)
//        spnDevice = findViewById(R.id.spnDevice)
//        spnCamera = findViewById(R.id.spnCamera)
        surfaceView = findViewById(R.id.surfaceView)
//        tvModelType=findViewById(R.id.tvModel)
//        spnModel=findViewById(R.id.spnModel)
        // btVideo=findViewById(R.id.btVideo)

        btHone.setOnClickListener {
            // 點擊按鈕時，啟動 MainActivity2
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
//            beatPlayer?.stop()
            beaterstop()

            // 關閉當前的 MainActivity
            finish()
        }

        val intent = Intent(this, MainResultActivity::class.java)
//        if(maxDiffDataList.size>4){
//            // 創建 Intent 將列表傳遞到 ResultActivity
////            val intent = Intent(this, ResultActivity::class.java)
//            intent.putParcelableArrayListExtra("maxDiffDataList", maxDiffDataList)
//            intent.putExtra("EXTRA_TIME", seconds)
//            startActivity(intent)
//
//
//            // 關閉當前的 MainActivity
//            finish()
//        }
        btResult.setOnClickListener {
            // 點擊按鈕時，啟動 MainActivity2
//            val intent = Intent(this, ResultActivity::class.java)
            intent.putParcelableArrayListExtra("maxDiffDataList", maxDiffDataList)
            intent.putExtra("EXTRA_TIME", seconds)
            startActivity(intent)
//            beatPlayer?.stop()
            beaterstop()

            // 關閉當前的 MainActivity
            finish()
        }

        val fastwarnPlayer = MediaPlayer.create(this, R.raw.fastvoice)
        var fastwarnPlayerFlag = true
        var fastCounter = 0
        val slowwarnPlayer = MediaPlayer.create(this, R.raw.slowvoice)
        var slowwarnPlayerFlag = true
        var slowCounter = 0
        val shallowPlayer = MediaPlayer.create(this, R.raw.shallowvoice)
        var shallowPlayerFlag = true
//        progressBar = findViewById(R.id.progressBar)
        val timer = timer(period = 400) {
            val maxDiff = dataArrayList.maxByOrNull { it.deep }

            if (maxDiffDataList.size >= 5 && uncprcount > 5) {
                // 創建 Intent 將列表傳遞到 ResultActivity
//                                            val intent = Intent(this, ResultActivity::class.java)
                intent.putParcelableArrayListExtra("maxDiffDataList", maxDiffDataList)
                intent.putExtra("EXTRA_TIME", seconds)
                startActivity(intent)
                beaterstop()

                uncprcount = 0
                // 關閉當前的 MainActivity
                finish()
            }

            if (maxDiff != null) {
                if (csvsave) {//只要csvsave為true，就進行儲存
                    writeToCsv(
                        maxDiff.deep,
                        maxDiff.frequency,
                        maxDiff.leftAngle,
                        maxDiff.rightAngle
                    ) // 將最大的數據寫入 CSV 檔案
                }
                runOnUiThread {
                    // 如果有最大的數據，則顯示在 TextView 中
                    if (maxDiff != null) {
//                        val frequency=maxDiff.frequency


                        tvDeep.text = getString(
                            R.string.tfe_pe_tv_deep,
                            "${maxDiff.deep} 公分"
                        )

                        tvFrequency.text = getString(
                            R.string.tfe_pe_tv_frequency,
                            "每分鐘${maxDiff.frequency} 下"
                        )
                        tvAngle.text = getString(
                            R.string.tfe_pe_tv_angle,
                            "雙手:${(maxDiff.leftAngle+maxDiff.rightAngle)/2}° "
                        )

//                        tvAngle.text = getString(
//                            R.string.tfe_pe_tv_angle,
//                            "左手:${maxDiff.leftAngle}° 右手:${maxDiff.rightAngle}°"
//                        )
                    }

                }

                // 清空 dataArrayList
                dataArrayList.clear()
            }
        }
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // 在這裡執行你的任務
                // 在 dataArrayList 中找到 averagewristYDiff 最大的數據
                val maxDiff = dataArrayList.maxByOrNull { it.deep }
                if (maxDiff != null) {
                    val frequency = maxDiff.frequency
                    if (maxDiff.deep < 3 && shallowPlayerFlag == true) {
                        shallowPlayer.start()
                        shallowPlayerFlag = false
                    } else {
                        shallowPlayerFlag = true

                        if (frequency > 120) {
                            slowCounter++
                            if (slowCounter > 3 && slowwarnPlayerFlag == true) {
                                slowwarnPlayer.start()
                                slowwarnPlayerFlag = false
                                fastCounter = 0
                            } else {
                                slowwarnPlayerFlag = true
                            }

                        } else if (frequency < 100) {
                            fastCounter++
                            if (fastCounter > 3 && fastwarnPlayerFlag == true) {
                                fastwarnPlayer.start()
                                fastwarnPlayerFlag = false
                                slowCounter = 0
                            } else {
                                fastwarnPlayerFlag = true
                            }
                        } else {
                            if (slowCounter > 3) {
                                slowCounter = 0
                            }
                            if (fastCounter > 3) {
                                fastCounter = 0
                            }

                            slowwarnPlayerFlag = true
                            fastwarnPlayerFlag = true
                        }
                    }
                }
                // 更新 TextView 顯示


            }
        }, 0, 400) // 開始的延遲時間為 0，間隔為 500 毫秒


        if (csvsave) {  //進行是否建立檔案
            csvread()

            csvread2()
        }
        setupPlayer()
        dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoPlayerDemo"))


        getContent =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    val uri = data?.data
                    val fileuri = getFilePathFromContentUri(this, uri!!)
                    val fileUri = Uri.parse("file://$fileuri")
                    // 在这里处理所选视频的Uri


                    if (fileuri != null) {
                        Toast.makeText(this, fileUri.toString(), Toast.LENGTH_SHORT).show()
                        // 处理所选视频的Uri
//                        val mediaItem = MediaItem.fromUri(fileUri)
//                        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoPlayerDemo"))
                        val mediaSource2 = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(fileUri)
//                        val mediaItem = MediaItem.fromUri(fileUri)
//                        player.addMediaItem(mediaItem)
                        concatenatingMediaSource.addMediaSource(0, mediaSource2)
                        player.setMediaSource(concatenatingMediaSource)
//                        player.addMediaItem(mediaItem)
                        player.prepare()


                    }

                }
            }



        initSpinner()
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }


    }

    override fun onStart() {
        super.onStart()
        openCamera()

    }

    suspend fun waitSecond() {

        while (true) {
//               if(flagwait){
            delay(200)
            flagwait = false
//               }
            break

        }

    }

    fun beater() {

        // 設定每個時間間隔執行的任務
//        timer.scheduleAtFixedRate(0, 50) {//共50毫秒5執行一次
//            beatPlayer?.start()
//        }
        val beatsPerMinute = 120 // 每分鐘的節拍數
        val interval = (60 * 1000 / beatsPerMinute).toLong()
        // 設定每個時間間隔執行的任務
        timer.scheduleAtFixedRate(0, interval) {
            count++
            println("節拍 $count")

            // 在單獨的執行緒中播放音樂
            Thread {
                beatPlayer?.start() // 播放聲音
            }.start()
        }

    }

    fun beaterstop() {

        beatPlayer?.release()
        beatPlayer = null

    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()

        setupPlayer()
        beater()
        //   addMP4()
        //   setFile()//開啟檔案
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        beaterstop()
        player.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        timer.cancel()
        Log.d(TAG, "onSaveInstanceState: " + player.currentPosition)
    }

    private fun updateTimer() {
        seconds++
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        // 格式化時間顯示，補零
        val timeString = String.format("時間:  %02d:%02d", minutes, remainingSeconds)
        tvTimer.text = timeString
    }


    /** 检查相机权限是否有授权 */
    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupPlayer() {//設置撥放器
        player = ExoPlayer.Builder(this).build()

        surfaceView.holder.addCallback(surfaceCallback)
        dataSourceFactory = DefaultDataSourceFactory(this, "ExoPlayerDemo")

//        playerView.player = player
        player.addListener(this)
        val uri1 =
            Uri.parse("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")

        val mediaSource1 = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(uri1)
        concatenatingMediaSource.addMediaSource(mediaSource1)


        player.setMediaSource(concatenatingMediaSource)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                createPoseEstimator()
                if (isPlaying) {
                    // 正在播放，開始偵測人體動作
                    // 在這裡加入你的人體偵測程式碼
                    createPoseEstimator()
                } else {
                    // 暫停或停止播放，停止偵測人體動作
                    // 在這裡加入你的停止偵測程式碼
                }
            }

            override fun onPositionDiscontinuity(reason: Int) {
                super.onPositionDiscontinuity(reason)
                // 播放位置發生變化，這可能意味著播放新的影片，需要停止之前的偵測
                // 在這裡加入你的停止偵測程式碼
            }
        })

        player.prepare()
        player.play()

    }

    fun getFilePathFromContentUri(context: Context, contentUri: Uri): String? {
        val filePath: String?
        val cursor = context.contentResolver.query(contentUri, null, null, null, null)
        if (cursor == null) {
            filePath = contentUri.path
        } else {
            cursor.moveToFirst()
            val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            filePath = cursor.getString(index)
            cursor.close()
        }
        return filePath
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            player.setVideoSurface(holder.surface)

        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // 不需要任何操作
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // 不需要任何操作
        }
    }

    fun csvread() {
        // 檢查並請求權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            requestMediaPermissions() // 請求權限
        } else {
            // 讀取 CSV 的邏輯
            val csvHeader = arrayOf("deep", "frequency", "Langle", "Rangle", "Timestamp")
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "cpr_$timestamp.csv"
            )
            if (!file.exists()) {
                file.createNewFile()
            }

            val fileWriter = FileWriter(file, false)
            bufferedWriter = BufferedWriter(fileWriter)

            bufferedWriter.append(csvHeader.joinToString(","))
            bufferedWriter.newLine()

            bufferedWriter.flush()
            bufferedWriter.close()
            fileWriter.close()
        }
    }

    fun writeToCsv(deep: Float, frequency: Int, leftAngle: Int, rightAngle: Int) {
        // 檢查並請求權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            requestMediaPermissions() // 請求權限
        } else {
            // 寫入 CSV 的邏輯
            try {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "cpr_$timestamp.csv"
                )
                val fileWriter = FileWriter(file, true)
                val bufferedWriter = BufferedWriter(fileWriter)

                val recordTime = SimpleDateFormat("HH:mm:ss").format(Date())
                bufferedWriter.write("$deep,$frequency,$leftAngle,$rightAngle,$recordTime")
                bufferedWriter.newLine()

                bufferedWriter.close()
                fileWriter.close()

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun writeCycleToCsv() {
        try {
            // 打開 CSV 文件，創建 FileWriter 和 BufferedWriter
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "cpr_$timestamp.csv"
            )//開檔案放到手機內存的Documents裡，讀cpr.csv檔
            val fileWriter = FileWriter(file, true)//false是從頭開始寫入資料，true為接下去寫入資料
            val bufferedWriter = BufferedWriter(fileWriter)

            bufferedWriter.write("Cycle:$cycleidx ")
            bufferedWriter.newLine()

            // 關閉 BufferedWriter 和 FileWriter
            bufferedWriter.close()
            fileWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun csvread2() {//進行csv的創建初始化
        val csvHeader = arrayOf("deep", "frequency", "Langle", "Rangle", "Timestamp")
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "cpr_t_$timestamp.csv"
        )//開檔案放到手機內存的Documents裡，讀cpr.csv檔

        if (!file.exists()) {//如果沒檔案
            file.createNewFile()//就創建cpr.csv檔
        }

        val fileWriter = FileWriter(file, false)//false是從頭開始寫入資料，true為接下去寫入資料
        bufferedWriter = BufferedWriter(fileWriter)

        bufferedWriter.append(csvHeader.joinToString(","))
        bufferedWriter.newLine()

        // 做完後別忘了要關閉 BufferedWriter 與 FileWriter
        bufferedWriter.flush()
        bufferedWriter.close()
        fileWriter.close()
    }

    fun writeToCsv2(deep: Float, frequency: Int, leftAngle: Int, rightAngle: Int) {
        try {

            // 打開 CSV 文件，創建 FileWriter 和 BufferedWriter
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "cpr_t_$timestamp.csv"
            )//開檔案放到手機內存的Documents裡，讀cpr.csv檔
            val fileWriter = FileWriter(file, true)//false是從頭開始寫入資料，true為接下去寫入資料
            val bufferedWriter = BufferedWriter(fileWriter)

            val recordTime = SimpleDateFormat("HH:mm:ss").format(Date())//紀錄時間，分鐘和秒
            // 寫入數據行
            bufferedWriter.write("$deep,$frequency,$leftAngle,$rightAngle,$recordTime")
            bufferedWriter.newLine()

            // 關閉 BufferedWriter 和 FileWriter
            bufferedWriter.close()
            fileWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun writeCycleToCsv2() {
        try {
            // 打開 CSV 文件，創建 FileWriter 和 BufferedWriter
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "cpr_t_$timestamp.csv"
            )//開檔案放到手機內存的Documents裡，讀cpr.csv檔
            val fileWriter = FileWriter(file, true)//false是從頭開始寫入資料，true為接下去寫入資料
            val bufferedWriter = BufferedWriter(fileWriter)
            bufferedWriter.write("Cycle:$cycleidx ")
            bufferedWriter.newLine()

            // 關閉 BufferedWriter 和 FileWriter
            bufferedWriter.close()
            fileWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun openCamera() {
        beatPlayer = MediaPlayer.create(this, R.raw.beat)
        /** 音频播放 */
        val armvoicePlayer = MediaPlayer.create(this, R.raw.armvoice)
        var armvoicePlayerFlag = true

        fun leftKeypoints(
        ): Int? {
            val pointA = VisualizationUtils.leftpointAA
            val pointB = VisualizationUtils.leftpointBB
            val pointC = VisualizationUtils.leftpointCC

            val pointAB = Math.sqrt(
                Math.pow(
                    (pointA.x - pointB.x).toDouble(),
                    2.0
                ) + Math.pow((pointA.y - pointB.y).toDouble(), 2.0)
            )
            val pointBC = Math.sqrt(
                Math.pow(
                    (pointB.x - pointC.x).toDouble(),
                    2.0
                ) + Math.pow((pointB.y - pointC.y).toDouble(), 2.0)
            )
            val pointAC = Math.sqrt(
                Math.pow(
                    (pointA.x - pointC.x).toDouble(),
                    2.0
                ) + Math.pow((pointA.y - pointC.y).toDouble(), 2.0)
            )

            if ((pointAB + pointBC) < pointAC || (pointBC + pointAC) < pointAB || (pointAB + pointAC) < pointBC
                || Math.abs(pointAB - pointBC) > pointAC || Math.abs(pointBC - pointAC) > pointAB || Math.abs(
                    pointAB - pointAC
                ) > pointBC
            ) {
                return NULL.toInt()
            } else {
                val angle = Math.toDegrees(
                    Math.acos(
                        ((Math.pow(pointBC, 2.0) + Math.pow(pointAB, 2.0) - Math.pow(
                            pointAC,
                            2.0
                        )) / (2 * pointBC * pointAB))
                    )
                ).toInt()
//            }
                return angle
            }
        }

        fun rightKeypoints(
        ): Int? {
            val pointA = VisualizationUtils.rightpointAA
            val pointB = VisualizationUtils.rightpointBB
            val pointC = VisualizationUtils.rightpointCC

            val pointAB = Math.sqrt(
                Math.pow(
                    (pointA.x - pointB.x).toDouble(),
                    2.0
                ) + Math.pow((pointA.y - pointB.y).toDouble(), 2.0)
            )
            val pointBC = Math.sqrt(
                Math.pow(
                    (pointB.x - pointC.x).toDouble(),
                    2.0
                ) + Math.pow((pointB.y - pointC.y).toDouble(), 2.0)
            )
            val pointAC = Math.sqrt(
                Math.pow(
                    (pointA.x - pointC.x).toDouble(),
                    2.0
                ) + Math.pow((pointA.y - pointC.y).toDouble(), 2.0)
            )

            if ((pointAB + pointBC) < pointAC || (pointBC + pointAC) < pointAB || (pointAB + pointAC) < pointBC
                || Math.abs(pointAB - pointBC) > pointAC || Math.abs(pointBC - pointAC) > pointAB || Math.abs(
                    pointAB - pointAC
                ) > pointBC
            ) {
                return NULL
            } else {
                val angle = Math.toDegrees(
                    Math.acos(
                        ((Math.pow(pointBC, 2.0) + Math.pow(pointAB, 2.0) - Math.pow(
                            pointAC,
                            2.0
                        )) / (2 * pointBC * pointAB))
                    )
                ).toInt()
//            }
                return angle
            }
        }

        suspend fun frequencyPress(callback: (Int, Float?) -> Unit) {
            var wristY1 = 0f
            var wristY2 = 0f
            var startTime: Long? = null
            var endTime: Long? = null
            var initialHeight: Float? = null
            var state = 0 // 0: 等待初始高度，1: 第一次按壓，2: 第二次按壓
            var isReadyForSecondCycle = false
            var outrange = false

            // 计算时间差
            val timediff = withContext(Dispatchers.Default) {
                // 記錄初始高度

                while (true) {
                    when (state) {
                        0 -> {
                            val wristY = VisualizationUtils.wristY
                            if (initialHeight == null || wristY < initialHeight!!) {
                                initialHeight = wristY
                            } else if (wristY > initialHeight!! + 5) {
                                wristY1 = wristY
                                state = 1
                            }
                        }
                        1 -> {
                            val wristY = VisualizationUtils.wristY
                            if (wristY > wristY1) {
                                wristY1 = wristY
                            } else if (wristY < wristY1 - 5 && Math.abs(initialHeight!! - wristY1) >= 9.5) {
                                startTime = System.currentTimeMillis()
                                isReadyForSecondCycle = false
                                state = 2
                            }
                        }
                        2 -> {
                            val wristY = VisualizationUtils.wristY
                            if (!isReadyForSecondCycle && Math.abs(wristY - initialHeight!!) < 5) {
                                isReadyForSecondCycle = true
                            }
                            if (wristY > wristY2 && isReadyForSecondCycle) {
                                wristY2 = wristY
                            } else if (wristY < wristY2 - 5 && isReadyForSecondCycle && Math.abs(
                                    wristY1 - wristY2
                                ) < 5
                            ) {//&& Math.abs(wristY - wristY2) > 5--------------
                                endTime = System.currentTimeMillis()

                                // 生產值，將時間差傳遞給收集器
//                                    send(endTime!! - startTime!!)
                                break
                            } else if (wristY1 != 0f && wristY2 != 0f && Math.abs(wristY1 - wristY2) > 70) {//防止y1和y2差距過大而建置的早停機制
                                endTime = System.currentTimeMillis()
                                outrange = true
                                break
                            }
                        }
                    }
                    delay(50)
                    // 返回时间差
                }
                (endTime!! - startTime!!).toInt()
            }

//            val maxValue = 45.0//230.0
//            val minValue = 24.0//180.0
//            val maxRatio = 7.5
//            val minRatio = 4.8

//            val rate=8.8  //一公分約為8.8像素


//            val rate=4   //距離安妮65公分版
//            val rate=4.1 //距離安妮85公分(平均)
//            val rate=3.8  ////距離安妮105公分(平均)
            val rate = 3.9 //距離安妮85公分(統計版)
//            val rate=3.4  ////距離安妮105公分(統計版)
            if (timediff < 500 && timediff > 300 && !outrange) {//&&timediff<=600&&
                // 調用回調函數，並傳遞時間差
                callback(
                    (500 - (500 - timediff) * 0.1).toInt(),
                    String.format("%.2f", (wristY2 - initialHeight!!) / rate).toFloat()
                )//
            }

            if (timediff >= 500 && timediff <= 600 && !outrange) {//
                // 調用回調函數，並傳遞時間差
                callback(
                    timediff,
                    String.format("%.2f", (wristY2 - initialHeight!!) / rate).toFloat()
                )//

            }

            if (timediff > 600 && timediff < 5000 && !outrange) {//&&timediff<=600&&
                // 調用回調函數，並傳遞時間差
                callback(
                    (600 + timediff * 0.01).toInt(),
                    String.format("%.2f", (wristY2 - initialHeight!!) / rate).toFloat()
                )//

            } else {//反之將此函式停掉
                return
            }
        }

        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource =
                    CameraSource(
                        surfaceView,
                        selectedCamera,
                        object : CameraSource.CameraSourceListener {
                            override fun onFPSListener(fps: Int) {

                                /** 解释一下，tfe_pe_tv 的意思：tensorflow example、pose estimation、text view */
                                tvFPS.text = getString(R.string.tfe_pe_tv_fps, fps)
                            }

                            /**對檢測結果進行處理*/
                            @SuppressLint("StringFormatMatches")
                            override fun onDetectedInfo(
                                personScore: Float?,
                                poseLabels: List<Pair<String, Float>>?
                            ) {
                                /** 分析目標姿態，給出提示*/
                                if (personScore != null && personScore > 0.3) {
                                    tvCycle.text =
                                        getString(R.string.tfe_pe_tv_cycle, "第 $cycleidx 循環")
                                    val angleLeft = leftKeypoints(

                                    )
                                    val angleRight = rightKeypoints(

                                    )
                                    lifecycleScope.launch {
                                        if (cprflag) {

                                            frequencyPress { timediff, wristYDiff ->
                                                var olddiff = timeDiffs.average()
                                                if (prefrequency != timediff) {
                                                    timeDiffs.addLast(timediff)
                                                    prefrequency = timediff
                                                }
                                                if (predeep != wristYDiff && wristYDiff!! < 7f && wristYDiff > 2f) {//
                                                    wristYDiffs.addLast(wristYDiff!!)
                                                    predeep = wristYDiff
                                                }

                                                if (timeDiffs.size > 15 && Math.abs(timeDiffs.average() - olddiff!!) > 100) {//判別是否有離群值，有離群值就去除掉
                                                    timeDiffs.removeLast()
                                                }
                                                if (timeDiffs.size > 2) {
                                                    timeDiffs.removeFirst()
                                                }
                                                if (wristYDiffs.size > windowSize) {
                                                    wristYDiffs.removeFirst()
                                                }

                                                val averageTimediff = timeDiffs.average()
                                                val averagewristYDiff = String.format(
                                                    "%.2f",
                                                    wristYDiffs.average().toFloat()
                                                )
                                                val frequency = 60f / (averageTimediff / 1000f)
                                                val csvData = CsvData(
                                                    averagewristYDiff.toFloat(),
                                                    frequency.toInt(),
                                                    angleLeft!!,
                                                    angleRight!!,
                                                )
                                                dataArrayList.add(csvData)
                                                dataArrayList_3.add(csvData)

                                                if (csvData.frequency >= 100.0 && csvData.frequency <= 120.0) {
                                                    frequencyCount++
                                                }
                                                if (csvData.deep in 5.0..6.0) {//只要深度為5到6公分之間，就進行增加
                                                    deepCount++
                                                }
                                                if (csvData.leftAngle > 165) {
                                                    leftAngleCount++
                                                }
                                                if (csvData.rightAngle > 165) {
                                                    rightAngleCount++
                                                }
                                                if (dataArrayList_3.size > 2) {
                                                    val maxDiff_3 =
                                                        dataArrayList_3.maxByOrNull { it.deep }

                                                    if (maxDiff_3 != null) {
                                                        totalDeep += maxDiff_3.deep
                                                        totalFrequency += maxDiff_3.frequency
                                                        totalLeftAngle += maxDiff_3.leftAngle
                                                        totalRightAngle += maxDiff_3.rightAngle
                                                        totalBothAngle += (maxDiff_3.leftAngle+maxDiff_3.rightAngle)/2

                                                        totalcount++

                                                        if (csvsave) {
                                                            writeToCsv2(
                                                                maxDiff_3.deep,
                                                                maxDiff_3.frequency,
                                                                maxDiff_3.leftAngle,
                                                                maxDiff_3.rightAngle
                                                            ) // 將最大的數據寫入 CSV 檔案
                                                        }
                                                        dataArrayList_3.clear()
                                                    }
                                                }
                                            }
                                        } else {
                                            tvDeep.text =
                                                getString(R.string.tfe_pe_tv_deep, "等待下一個循環")
                                            tvFrequency.text = getString(
                                                R.string.tfe_pe_tv_frequency,
                                                "等待下一個循環"
                                            )

                                            tvAngle.text = getString(
                                                R.string.tfe_pe_tv_angle,
                                                "等待下一個循環"
                                            )
                                            if (willCycle) {
                                                if (maxDiffDataList.size < 5) {//&&totalDeep!=0.0

                                                    if (totalcount != 0) {
                                                        maxDiffDataList.add(
                                                            MaxDiffData(   //計算所有值的平均
                                                                (totalDeep.toFloat() / totalcount) ,
                                                                (totalFrequency.toFloat() / totalcount) ,
                                                                (totalBothAngle.toFloat() / totalcount) ,                                                                true
                                                            )
                                                        )
                                                        totalcount = 0
                                                        deepCount = 0
                                                        frequencyCount = 0
                                                        leftAngleCount = 0
                                                        rightAngleCount = 0
                                                        totalDeep = 0.0
                                                        totalFrequency = 0
                                                        totalLeftAngle = 0
                                                        totalRightAngle = 0
                                                        totalBothAngle=0
                                                        willCycle = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val sortedLabels = poseLabels!!.sortedByDescending { it.second }
                                    if (cprflag) {
                                        missingCounter = 0
                                        if ((angleLeft ?: 0 >= 165) && (angleRight ?: 0 >= 165)) {
                                            TGreater165Counter++
                                            poseRegister = "雙手大於165度"

                                            if (TGreater165Counter > 50) {

                                                /** 播放提示音 */
                                            } else if (TGreater165Counter > 10) {
                                                TGreater165Counter++
                                                L165Counter = 0
                                                R165Counter = 0

                                                TLess165Counter = 0
                                            }

                                            /** 顯示 Debug 信息 */
                                        } else if ((angleLeft ?: 0 < 165) || (angleRight ?: 0 < 165)) {
                                            TLess165Counter++

                                            poseRegister = "雙手小於165度"

                                            /** 顯示當前坐姿狀態：脖子前伸 */
                                            if (TLess165Counter > 100) {
                                                TLess165Counter = 0
                                            } else if (TLess165Counter > 50) {

                                                /** 播放提示音 */
                                                if (armvoicePlayerFlag) {
                                                    armvoicePlayer.start()
                                                }
                                                armvoicePlayerFlag = false
                                            } else if (TLess165Counter > 10) {
                                                TLess165Counter++
                                                L165Counter = 0
                                                R165Counter = 0
                                                TGreater165Counter = 0
                                                armvoicePlayerFlag = true
                                            }
                                            /** 顯示 Debug 信息 */
                                        }
                                        else if (angleLeft == NULL.toInt() || angleRight == NULL.toInt()) {
                                            tvAngle.text =
                                                getString(R.string.tfe_pe_tv_angle, "未偵測到 ")
                                        }
                                    }
                                    when (sortedLabels[0].first) {
                                        "cpr" -> {
                                            cprflag = true
                                            cprcount++
                                            uncprcount = 0

                                            if (cprcount == 1 && System.currentTimeMillis() - preTime > 2000) {
                                                cycleidx = maxDiffDataList.size + 1
                                                if (csvsave) {//只要csvsave有開得的話就儲存
                                                    writeCycleToCsv()
                                                    writeCycleToCsv2()
                                                }
                                                preTime = System.currentTimeMillis()
                                                willCycle = true

                                            }
                                        }
                                        "uncpr" -> {
                                            cprflag = false
                                            uncprcount++
                                            if (uncprcount > 5) {
                                                cprcount = 0
                                            }
                                        }
                                    }

                                } else {
                                    missingCounter++
                                    if (missingCounter > 30) {
                                    }
                                    tvCycle.text =
                                        getString(R.string.tfe_pe_tv_cycle, "第 $cycleidx 循環")
                                    tvDeep.text = getString(R.string.tfe_pe_tv_deep, "未偵測到")
                                    tvFrequency.text =
                                        getString(R.string.tfe_pe_tv_frequency, "未偵測到")
                                    /** 顯示 Debug 信息 */
//
                                    tvAngle.text = getString(R.string.tfe_pe_tv_angle, "未偵測到")
                                }
                            }
                        }).apply {
                        prepareCamera()
                    }
                isPoseClassifier()
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                }
            }
            createPoseEstimator()
        }
    }

    private fun isPoseClassifier() {
        cameraSource?.setClassifier(if (isClassifyPose) PoseClassifier.create(this) else null)
    }

    /** 初始化運算設備選項菜單（CPU、GPU、NNAPI） */
    private fun initSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_device_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_camera_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_model_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    /** 在程序運行過程中切換運行計算設備*/
    private fun changeDevice(position: Int) {
        val targetDevice = Device.NNAPI
        if (device == targetDevice) return
        device = targetDevice
        createPoseEstimator()
    }

    /**在程序運行過程中切換攝像頭 */
    private fun changeCamera(direaction: Int) {
        val targetCamera = Camera.BACK
        if (selectedCamera == targetCamera) return
        selectedCamera = targetCamera

        cameraSource?.close()
        cameraSource = null
        openCamera()
    }

    private fun changeModel(direaction: Int) {
        val targetModel = ModelType.Thunder
        if (model == targetModel) return
        model = targetModel

        createPoseEstimator()
    }


    private fun createPoseEstimator() {
        val poseDetector = MoveNet.create(this, device, model)
        poseDetector.let { detector ->
            cameraSource?.setDetector(detector)
        }
    }

    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VIDEO_FILE && resultCode == Activity.RESULT_OK && data != null) {
            val videoUri = data.data
            // 使用videoUri讀取影片文件
            // ...
        }
    }

    /**顯示報錯信息*/
    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // pass
                }
                .create()

        companion object {
            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}
