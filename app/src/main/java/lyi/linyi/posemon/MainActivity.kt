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
    val leftAngle: Float,
    val rightAngle: Float,
    val isCycleCompleted: Boolean
) : Parcelable {
    // 實現 Parcelable 接口的必要方法

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeFloat(deep)
        dest.writeFloat(frequency)
        dest.writeFloat(leftAngle)
        dest.writeFloat(rightAngle)
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
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readByte() != 0.toByte()
    )
}

class MainActivity : AppCompatActivity(), Player.Listener {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
        const val REQUEST_VIDEO_FILE = 1
//        private val permissions = arrayOf(
//            "android.permission.READ_EXTERNAL_STORAGE" ,
//            "android.permission.WRITE_EXTERNAL_STORAGE"
//        )

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

    private lateinit var btResult: Button
    private lateinit var btHone: Button

    private lateinit var tvTimer: TextView
    private var seconds = 0
    private val handler = Handler()
    private lateinit var runnable: Runnable

//    private lateinit var ivStatus: ImageView

    private lateinit var tvFPS: TextView
    private lateinit var tvCycle: TextView
    private lateinit var tvDeep: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvAngle: TextView
//    private lateinit var spnDevice: Spinner
//    private lateinit var spnCamera: Spinner
//    private lateinit var spnModel: Spinner
    //  private  lateinit var btVideo: Button

    private lateinit var getContent: ActivityResultLauncher<Intent>
    private lateinit var player: ExoPlayer
    var beatPlayer: MediaPlayer? = null

    // private lateinit var playerView: SurfaceView
//    private lateinit var progressBar: ProgressBar
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
            val intent = Intent(this, MainActivity2::class.java)
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
                // 將 maxDiffData 添加到列表中
//                totalDeep+=maxDiff.deep
//                totalFrequency+= maxDiff.frequency
//                totalLeftAngle+=maxDiff.leftAngle
//                totalRightAngle+= maxDiff.rightAngle
//                totalcount++
//                if(maxDiffDataList.size<6) {
//                    if(willCycle==cycleidx){
//                        willCycle++
//                    maxDiffDataList.add(
////                        MaxDiffData(
////                            maxDiff.deep,
////                            maxDiff.frequency,
////                            maxDiff.leftAngle,
////                            maxDiff.rightAngle
////                        )
//                        MaxDiffData(
//                            (totalDeep/totalcount).toFloat(),
////                            maxDiff.deep,
//                            totalFrequency/totalcount,
//                            totalLeftAngle/totalcount,
//                            totalRightAngle/totalcount
//                        )
//                    )
//                    totalcount=0
//                    }
//                }
//                if(maxDiffDataList.size==5){
//                if(maxDiffDataList.size>=1 && uncprcount>5){
//                    // 創建 Intent 將列表傳遞到 ResultActivity
////                                            val intent = Intent(this, ResultActivity::class.java)
//                    intent.putParcelableArrayListExtra("maxDiffDataList", maxDiffDataList)
//                    intent.putExtra("EXTRA_TIME", seconds)
//                    startActivity(intent)
//                    beaterstop()
//
//
//                    // 關閉當前的 MainActivity
//                    finish()
//                }
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
                            "左手:${maxDiff.leftAngle}° 右手:${maxDiff.rightAngle}°"
                        )
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

//    fun playSound() {
//        // 在這裡添加播放聲音的程式碼
////        println("播放聲音")
//        // 請在這裡添加播放音樂的程式碼，可以參考前述的音樂播放程式碼示例
//    }


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
//    private fun addMP4() {
//
//        val uri1 = Uri.parse("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")
//
//        val mediaSource1 = ProgressiveMediaSource.Factory(dataSourceFactory)
//            .createMediaSource(uri1)
//        concatenatingMediaSource.addMediaSource(mediaSource1)
//
//
//        player.setMediaSource(concatenatingMediaSource)
//
//        player.prepare()
////        player.play()
//    }


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


    //    fun csvread() {
////        val fileName = "cpr.csv"
////        val csvHeader = arrayOf("deep", "frequency", "Langle","Rangle")
////        val csvData = arrayOf(
////            arrayOf(wristYDiff, frequency.toInt(), angleLeft,angleRight),
////        )
////        val csvData = arrayOf()
//
////        val directory = this.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
//
////        val file = File(directory, fileName)
//
//        if (!file.exists()) {
//            file.createNewFile()
//            fileWriter.append(csvHeader.joinToString(","))
//            fileWriter.append("\n")
//        }
//        dataList.forEach { data ->
//            fileWriter.append(data.joinToString(","))
//            fileWriter.append("\n")
//        }
//        fileWriter.flush()
//        fileWriter.close()
//}
//    fun writeToCsv(data: Array<String>) {
//        dataList.add(data)
//    }
    fun csvread() {//進行csv的創建初始化
        val csvHeader = arrayOf("deep", "frequency", "Langle", "Rangle", "Timestamp")
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "cpr_$timestamp.csv"
        )//開檔案放到手機內存的Documents裡，讀cpr.csv檔


//    val file = File(directory, fileName)
        if (!file.exists()) {//如果沒檔案
            file.createNewFile()//就創建cpr.csv檔
        }

        val fileWriter = FileWriter(file, false)//false是從頭開始寫入資料，true為接下去寫入資料
        bufferedWriter = BufferedWriter(fileWriter)

        bufferedWriter.append(csvHeader.joinToString(","))
        bufferedWriter.newLine()
//
//    // 在這個範例程式碼中，我們假設你要持續寫入多筆資料，
//    // 你可以在迴圈中不斷呼叫以下程式碼
//    val csvData = arrayOf(wristYDiff, frequency.toInt(), angleLeft, angleRight)
//    bufferedWriter.append(csvData.joinToString(","))
//    bufferedWriter.newLine()

        // 做完後別忘了要關閉 BufferedWriter 與 FileWriter
        bufferedWriter.flush()
        bufferedWriter.close()
        fileWriter.close()
    }

    fun writeToCsv(deep: Float, frequency: Int, leftAngle: Int, rightAngle: Int) {
        try {


            // 打開 CSV 文件，創建 FileWriter 和 BufferedWriter
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "cpr_$timestamp.csv"
            )//開檔案放到手機內存的Documents裡，讀cpr.csv檔
            val fileWriter = FileWriter(file, true)//false是從頭開始寫入資料，true為接下去寫入資料
            val bufferedWriter = BufferedWriter(fileWriter)

//            if(System.currentTimeMillis()-preTime>2000){
//                System.currentTimeMillis()
//                bufferedWriter.write("Cycle:$cycleidx")
//                bufferedWriter.newLine()
//                cycleidx++
//                preTime=System.currentTimeMillis()
//
//            }

//            if(uncprcount==1){
//                System.currentTimeMillis()
//                bufferedWriter.write("Cycle:$cycleidx")
//                bufferedWriter.newLine()
//                cycleidx++
//                preTime=System.currentTimeMillis()
//
//
//            }


//            if(cprflag) {
            val recordTime = SimpleDateFormat("HH:mm:ss").format(Date())//紀錄時間，分鐘和秒
            // 寫入數據行
            bufferedWriter.write("$deep,$frequency,$leftAngle,$rightAngle,$recordTime")
            bufferedWriter.newLine()
//            }

            // 關閉 BufferedWriter 和 FileWriter
            bufferedWriter.close()
            fileWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
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

//            if(System.currentTimeMillis()-preTime>2000){
//                System.currentTimeMillis()
//                bufferedWriter.write("Cycle:$cycleidx")
//                bufferedWriter.newLine()
//                cycleidx++
//                preTime=System.currentTimeMillis()
//
//            }

//            if(uncprcount==1){
//                cycleidx++
            bufferedWriter.write("Cycle:$cycleidx ")
            bufferedWriter.newLine()


//            }


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
//            }

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
//            cycleidx++
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
//        val crosslegPlayer = MediaPlayer.create(this, R.raw.crossleg)
//        val forwardheadPlayer = MediaPlayer.create(this, R.raw.forwardhead)


        val armvoicePlayer = MediaPlayer.create(this, R.raw.armvoice)
        var armvoicePlayerFlag = true
        /*
        val fastwarnPlayer= MediaPlayer.create(this, R.raw.fastvoice)
        var fastwarnPlayerFlag=true
        val slowwarnPlayer= MediaPlayer.create(this, R.raw.slowvoice)
        var slowwarnPlayerFlag=true
        */



        fun leftKeypoints(
//            persons: List<Person>,
//            l: Triple<BodyPart, BodyPart, BodyPart>
        ): Int? {


//            persons.forEach { person ->
//                val pointA = person.keyPoints[l.first.position].coordinate
//                val pointB = person.keyPoints[l.second.position].coordinate
//                val pointC = person.keyPoints[l.third.position].coordinate

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
//            persons: List<Person>,
//            l: Triple<BodyPart, BodyPart, BodyPart>
        ): Int? {


//            persons.forEach { person ->
//                val pointA = person.keyPoints[l.first.position].coordinate
//                val pointB = person.keyPoints[l.second.position].coordinate
//                val pointC = person.keyPoints[l.third.position].coordinate

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
//        fun frequencyPress(): Float {
//            var wristY=VisualizationUtils.wristpoint
//            var wristY1=1000f
//            var wristY2=1000f
//            var wristFlag=true
//            var Time1=1f
//            var Time2=1f
//
//            while (wristFlag){
//                if(wristY<wristY1){
//                    wristY1=wristY
////                    wristY=VisualizationUtils.wristpoint
//
//                }
//
//             else if (wristY>=wristY1) {
//
//                    wristY1=1000f
//                    Time1= System.currentTimeMillis().toFloat()
//                    wristFlag=false
//
//                }
//
//
//
//             }


/*
         suspend fun frequencyPress(): Long = withContext(Dispatchers.IO) {
            var wristY1 = 0f
            var wristY2 = 0f
            var startTime: Long? = null
            var endTime: Long? = null
            var initialHeight: Float? = null
            var isReadyForSecondCycle = false
            var state = 0 // 0: 等待初始高度，1: 第一次按壓，2: 第二次按壓

            // 使用 produce 管理狀態變化和時間計算
            val flow = produce<Long> {
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
                            } else if (wristY < wristY1 - 5 && Math.abs(initialHeight!! - wristY1) >= 25.5) {
                                startTime = System.currentTimeMillis()
                                isReadyForSecondCycle = false
                                state = 2
                            }
                        }
                        2 -> {
                            val wristY = VisualizationUtils.wristY
                            if (!isReadyForSecondCycle && Math.abs(wristY - initialHeight!!) < 8) {
                                isReadyForSecondCycle = true
                            }
                            if (wristY > wristY2 && isReadyForSecondCycle) {
                                wristY2 = wristY
                            } else if (wristY < wristY2 - 5 && isReadyForSecondCycle && Math.abs(wristY1 - wristY2) < 5 && Math.abs(wristY - wristY2) > 5) {
                                endTime = System.currentTimeMillis()
                                wristY2 = 0f
                                wristY1 = 0f
                                initialHeight = null
                                state = 0

                                // 生產值，將時間差傳遞給收集器
                                send(endTime!! - startTime!!)
                                break
                            }
                        }
                    }
                    delay(20)
                }
            }

            // 只接收第一次生產的值，也就是手勢辨識完成時的時間差
            flow.receive()
        }
*/





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
//                            else if (wristY < wristY1 - 5 && Math.abs(initialHeight!! - wristY1) < 15.5) {
//                            else if (wristY < wristY1 - 5 && Math.abs(initialHeight!! - wristY1) < 10.5) {
//                                wristY1 =0f
//                                state = 0
//                            }
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
//                                wristY2 = 0f
//                                wristY1 = 0f
//                                initialHeight = null
//                                state = 0

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
//                count++
//                callback(timediff)
//                if(VisualizationUtils.wristY-VisualizationUtils.shoulderY>230) {
//                    callback(500, String.format("%.2f", (wristY2 - initialHeight!!) / 7.5).toFloat())
//                }else if(VisualizationUtils.wristY-VisualizationUtils.shoulderY<=230&&VisualizationUtils.wristY-VisualizationUtils.shoulderY>=180){
//                    callback(500, String.format("%.2f", (wristY2 - initialHeight!!) / 5).toFloat())
//                }else if(VisualizationUtils.wristY-VisualizationUtils.shoulderY>0){
//                    callback(500, String.format("%.2f", (wristY2 - initialHeight!!) / 4).toFloat())
//                }
//                else return
//                val dif=wristY2 - initialHeight!!//VisualizationUtils.wristY-VisualizationUtils.shoulderY
//                val rate = when {
//                    dif > maxValue -> maxRatio
//                    dif in minValue..maxValue->     (dif - minValue) / (maxValue - minValue)*(maxRatio - minRatio) + minRatio//5.0f
//                    dif in 12.0..minValue ->minRatio
//                    else -> return
//                }

//                callback((timediff).toInt(), String.format("%.2f", (wristY2 - initialHeight!!)/ rate).toFloat())// 105公分版
//                callback((500-(500-timediff)*0.2).toInt(), String.format("%.2f", (wristY2 - initialHeight!!)/ rate).toFloat())// 105公分版
                callback(
                    (500 - (500 - timediff) * 0.1).toInt(),
                    String.format("%.2f", (wristY2 - initialHeight!!) / rate).toFloat()
                )//
            }

            if (timediff >= 500 && timediff <= 600 && !outrange) {//
                // 調用回調函數，並傳遞時間差
//                count2++
//                callback(timediff,String.format("%.2f", (wristY2- initialHeight!!)/6).toFloat())
//                if(VisualizationUtils.wristY-VisualizationUtils.shoulderY>230) {
//                    callback(timediff, String.format("%.2f", (wristY2 - initialHeight!!) / 7.5).toFloat())
//                }else if(VisualizationUtils.wristY-VisualizationUtils.shoulderY<=230&&VisualizationUtils.wristY-VisualizationUtils.shoulderY>=180){
//                    callback(timediff, String.format("%.2f", (wristY2 - initialHeight!!) / 5).toFloat())
//                }else if(VisualizationUtils.wristY-VisualizationUtils.shoulderY>0){
//                    callback(timediff, String.format("%.2f", (wristY2 - initialHeight!!) / 4).toFloat())
//                }
//                else return
//                val dif=wristY2 - initialHeight!!//VisualizationUtils.wristY-VisualizationUtils.shoulderY
//                val rate = when {
//                    dif > maxValue -> maxRatio
//                    dif in minValue.toDouble()..maxValue.toDouble()->     (dif - minValue) / (maxValue - minValue)*(maxRatio - minRatio) + minRatio//5.0f
//                    dif in 12.0..minValue.toDouble() ->minRatio
//                    else -> return
//                }
                callback(
                    timediff,
                    String.format("%.2f", (wristY2 - initialHeight!!) / rate).toFloat()
                )//

            }

            if (timediff > 600 && timediff < 5000 && !outrange) {//&&timediff<=600&&
                // 調用回調函數，並傳遞時間差
//                count3++
//                callback(timediff)
//                callback((600+(timediff-600)*0.05).toInt(),String.format("%.2f", (wristY2- initialHeight!!)/6).toFloat())
//                if(VisualizationUtils.wristY-VisualizationUtils.shoulderY>230) {
//                    callback(600, String.format("%.2f", (wristY2 - initialHeight!!) / 7.5).toFloat())
//                }else if(VisualizationUtils.wristY-VisualizationUtils.shoulderY<=230&&VisualizationUtils.wristY-VisualizationUtils.shoulderY>=180){
//                    callback(600, String.format("%.2f", (wristY2 - initialHeight!!) / 5).toFloat())
//                }else if(VisualizationUtils.wristY-VisualizationUtils.shoulderY>0){
//                    callback(600, String.format("%.2f", (wristY2 - initialHeight!!) / 4).toFloat())
//                }
//                else return
//                val dif=wristY2 - initialHeight!!//VisualizationUtils.wristY-VisualizationUtils.shoulderY
//                val rate = when {
//                    dif > maxValue -> maxRatio
//                    dif in minValue.toDouble()..maxValue.toDouble()->     (dif - minValue) / (maxValue - minValue)*(maxRatio - minRatio) + minRatio//5.0f
//                    dif in 12.0..minValue.toDouble() ->minRatio
//                    else -> return
//                }
//                callback((timediff).toInt(), String.format("%.2f", (wristY2 - initialHeight!!)/ rate).toFloat())//
                callback(
                    (600 + timediff * 0.01).toInt(),
                    String.format("%.2f", (wristY2 - initialHeight!!) / rate).toFloat()
                )//

            } else {//反之將此函式停掉
                return
            }
        }


//        val outputBitmap = VisualizationUtils.drawBodyKeypoints(
//
//            persons.filter { it.score > CameraSource.MIN_CONFIDENCE }, isTrackerEnabled
//        )


//                Triple(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_WRIST),


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
//                            tvFPS.text = getString(R.string.tfe_pe_tv_fps,"fps)
                            }

                            /**對檢測結果進行處理*/
                            @SuppressLint("StringFormatMatches")
                            override fun onDetectedInfo(
                                personScore: Float?,
                                poseLabels: List<Pair<String, Float>>?
                            ) {


//                            tvFrequency.text = getString(R.string.tfe_pe_tv_frequency, personScore ?: 0f)

                                /** 分析目標姿態，給出提示*/
                                if (personScore != null && personScore > 0.3) {

//                                val persons = mutableListOf<Person>()

                                    tvCycle.text =
                                        getString(R.string.tfe_pe_tv_cycle, "第 $cycleidx 循環")
//                                tvCycle.text =
//                                    getString(R.string.tfe_pe_tv_cycle, "The ${maxDiffDataList.size} cycle")


//                                tvFrequency.text = getString(R.string.tfe_pe_tv_frequency, " ${ VisualizationUtils.wristY.toInt()} 下")

//                                val windowSize = 10
//                                val timeDiffs = ArrayDeque<Long>(windowSize)
                                    val angleLeft = leftKeypoints(

                                    )
                                    val angleRight = rightKeypoints(

                                    )

//                                tvAngle.text = getString(R.string.tfe_pe_tv_angle, String.format("右手手腕%.2f", VisualizationUtils.wristY))

                                    lifecycleScope.launch {


//                                GlobalScope.launch {
//                                    while (true) {
//                                        var timediff = async { frequencyPress() }.await()
//                                    var timediff = frequencyPress()
//                                    timeDiffs.addLast(timediff)//

//                                   if(timediff>=500 ) {
//                                          timeDiffs.addLast(timediff)

//                                    if(!flagwait){
//                                        flagwait=true
                                        if (cprflag) {

                                            frequencyPress { timediff, wristYDiff ->
//                                        if(timediff>=500&&timediff<=1200) {
                                                var olddiff = timeDiffs.average()

//
                                                if (prefrequency != timediff) {
                                                    timeDiffs.addLast(timediff)
                                                    prefrequency = timediff
                                                }
//                                        if(predeep!=wristYDiff&& wristYDiff!! <7f&&wristYDiff>1.4f){//
                                                if (predeep != wristYDiff && wristYDiff!! < 7f && wristYDiff > 2f) {//
                                                    wristYDiffs.addLast(wristYDiff!!)
                                                    predeep = wristYDiff
                                                }


                                                if (timeDiffs.size > 15 && Math.abs(timeDiffs.average() - olddiff!!) > 100) {//判別是否有離群值，有離群值就去除掉
                                                    timeDiffs.removeLast()
                                                }
//                                        wristYDiffs.addLast(wristYDiff!!)

//                                        }
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

/*
                                        if(averageTimediff<500&&slowwarnPlayerFlag==true){
                                            slowwarnPlayer.start()
                                            slowwarnPlayerFlag=false
                                        }else if(averageTimediff>600&&fastwarnPlayerFlag){
                                            fastwarnPlayer.start()
                                            fastwarnPlayerFlag=false
                                        }else{
                                            slowwarnPlayerFlag=true
                                            fastwarnPlayerFlag=true
                                        }
                                        */
                                                // 判断时间差是否稳定
//                                       if (averageTimediff >= 500 && averageTimediff<=1200) {
                                                val frequency = 60f / (averageTimediff / 1000f)
//                                        val frequency = 60f / (averageTimediff / 1000f)

//                                    if(wristYDiff!!<=7&& wristYDiff!! >=4.5) {

//                                        if(cprflag) {
//                                        val csvData = CsvData(averagewristYDiff.toFloat(), frequency.toInt(), angleLeft!!, angleRight!!,)

                                                val csvData = CsvData(
                                                    averagewristYDiff.toFloat(),
                                                    frequency.toInt(),
                                                    angleLeft!!,
                                                    angleRight!!,
                                                )
                                                dataArrayList.add(csvData)
                                                dataArrayList_3.add(csvData)
                                                totalcount++
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
//                                                totalcount++
//                                                if(maxDiff_3.frequency>=100 && maxDiff_3.frequency<=120){
//                                                    frequencyCount++
//                                                }
//                                                if(maxDiff_3.deep>=5.0 && maxDiff_3.deep<=6.0){
//                                                    deepCount++
//                                                }
//                                                if(maxDiff_3.leftAngle >165){
//                                                    leftAngleCount++
//                                                }
//                                                if(maxDiff_3.rightAngle >165){
//                                                    rightAngleCount++
//                                                }


//                                                }
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


                                                /*

                                                    writeToCsv(
                                                        averagewristYDiff.toFloat(),
                                                        frequency.toInt(),
                                                        angleLeft!!,
                                                        angleRight!!
                                                    )//將得到的資料讀到csv檔裡
        //                                        }
                                                */


// 設置定時器的取消任務
//                                        timer.cancel()
/*
                                        tvDeep.text =
                                            getString(R.string.tfe_pe_tv_deep, "$averagewristYDiff cm")
//                                        tvDeep.text =
////                                            getString(R.string.tfe_pe_tv_deep, "$averagewristYDiff 公分")
//                                        tvFrequency.text = getString(
//                                            R.string.tfe_pe_tv_frequency,
//                                            "每分鐘做 ${frequency.toInt()} 下"
//                                        )

                                        tvFrequency.text = getString(
                                            R.string.tfe_pe_tv_frequency,
                                            "${frequency.toInt()} bpm"
//                                            "每分鐘做 ${timediff.toInt()} 下"
                                        )
                                        tvAngle.text = getString(
                                            R.string.tfe_pe_tv_angle,
                                            "Left:$angleLeft° Right:$angleRight°"
                                        )
*/
//                                    }
//                                        tvAngle.text = getString(R.string.tfe_pe_tv_angle, String.format("肩手差距%.2f", VisualizationUtils.wristY-VisualizationUtils.shoulderY))

//                                        tvFrequency.text = getString(R.string.tfe_pe_tv_frequency, "c1:${count} c2:${count2} c3:${count3}")
//

//                                        }
//                                        flagwait=true
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
                                                            MaxDiffData(   //計算有達到的次數
                                                                (deepCount.toFloat() / totalcount) * 100,
                                                                (frequencyCount.toFloat() / totalcount) * 100,
                                                                (leftAngleCount.toFloat() / totalcount) * 100,
                                                                (rightAngleCount.toFloat() / totalcount) * 100
                                                            )


//                                                    MaxDiffData(   //計算所有德的平均
//                                                        (totalDeep / totalcount).toFloat(),
//                                                        totalFrequency.toFloat() / totalcount,
//                                                        totalLeftAngle.toFloat() / totalcount,
//                                                        totalRightAngle.toFloat() / totalcount
//                                                    )
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
                                                        willCycle = false
                                                    }
                                                }

                                            }
                                        }

                                        // 维护窗口大小


//                                    }
//                                    else{
//                                    while(true) {
//
//                                        if (timediff >= 500) {
//                                            timeDiffs.addLast(timediff)
////                                            avager=receiveData(frequency)
//                                            TimeAdd = 0
//                                            break
//                                        } else if (TimeAdd >= 500 &&TimeAdd<=1200) {
//                                            timeDiffs.addLast(TimeAdd)
//
//
////                                            avager=receiveData(frequency)
//                                            TimeAdd = 0
//                                            break
//                                        } else if (TimeAdd + timediff >= 500 &&TimeAdd + timediff<=1200) {
//                                            timeDiffs.addLast((TimeAdd + timediff))
//
//
//                                            TimeAdd = 0
//                                            break
//                                        }
//                                        else if (TimeAdd>1200||TimeAdd + timediff>1200){
//                                            TimeAdd=1200
//
//                                        }else
//                                         {
//                                            TimeAdd += timediff
//                                            timediff = async { frequencyPress() }.await()
//
//                                        }
//                                    }
//                                    }


//                                            timeDiffs.clear()
//                                       }


//                                    }


//                                }
//                                    else {
//                                        delay(1000)
//                                        flagwait=false
//                                        waitSecond()
                                    }


//                                }


/*
                                val alpha = 0.3  // 平滑系数
                                var smoothedFrequency = 0f  // 平滑后的按压频率
                                var lastTime = System.currentTimeMillis()  // 上次按压时间
                                var lastFrequency = 0f  // 上次计算的按压频率

                                GlobalScope.launch {
                                    while (true) {
                                        val timediff = async { frequencyPress() }.await()

                                        // 计算当前的按压频率
                                        val currentFrequency = if (timediff >= 500) {
                                            60f / (timediff / 1000f)
                                        } else {
                                            lastFrequency
                                        }

                                        // 对按压频率进行指数平滑处理
                                        smoothedFrequency = (alpha * currentFrequency + (1 - alpha) * smoothedFrequency).toFloat()

                                        // 更新界面显示
                                        withContext(Dispatchers.Main) {
                                            tvFrequency.text = getString(R.string.tfe_pe_tv_frequency, "每分鐘做 ${smoothedFrequency.toInt()} 下")
                                        }

                                        // 更新上次按压时间和按压频率
                                        lastTime = System.currentTimeMillis()
                                        lastFrequency = currentFrequency

                                        // 停止 200 毫秒再继续监测
                                        delay(1000)
                                    }
                                }*/


//                                val bodyJoints = Triple(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW,BodyPart.LEFT_WRIST)
//                                val bodyJoints2 = Triple(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_WRIST)
//                                val angleLeft = leftKeypoints(
////                                    persons
//                                )
//                                val angleRight= rightKeypoints(
////                                    persons
//                                )
                                    val sortedLabels = poseLabels!!.sortedByDescending { it.second }
                                    if (cprflag) {
                                        missingCounter = 0


//                                while (personScore>0.3) {
                                        if ((angleLeft ?: 0 >= 165) && (angleRight ?: 0 >= 165)) {
//                                        crosslegCounter = 0
//                                        standardCounter = 0


//                                    if (poseRegister == "雙手大於165度") {

                                            TGreater165Counter++
//                                    }

                                            poseRegister = "雙手大於165度"


                                            if (TGreater165Counter > 50) {

                                                /** 播放提示音 */
//                                        if (armvoicePlayerFlag) {
//                                            armvoicePlayer.start()
//                                        }
//                                        armvoicePlayerFlag=false


//                                            standardPlayerFlag = true
//                                            crosslegPlayerFlag = true
//                                            forwardheadPlayerFlag = true
//                                            KIM1PlayerFlag=true
//                                            KIM2PlayerFlag=true
//                                            KIM3PlayerFlag=false
//                                            KIM4PlayerFlag=true

//                                        ivStatus.setImageResource(R.drawable.tgreater165)
                                            } else if (TGreater165Counter > 10) {
                                                TGreater165Counter++
                                                L165Counter = 0
                                                R165Counter = 0

                                                TLess165Counter = 0
//                                        ivStatus.setImageResource(R.drawable.tgreater165)
//                                        armvoicePlayerFlag=true
                                            }

                                            /** 顯示 Debug 信息 */
//                                    tvAngle.text = getString(R.string.tfe_pe_tv_angle, "左手:$angleLeft 度 右手:$angleRight 度")
//                                    tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $TGreater165Counter")
                                        } else if ((angleLeft ?: 0 < 165) || (angleRight ?: 0 < 165)) {
//                                        crosslegCounter = 0
//                                        standardCounter = 0


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

//                                            standardPlayerFlag = true
//                                            crosslegPlayerFlag = true
//                                            forwardheadPlayerFlag = true
//                                            KIM1PlayerFlag=true
//                                            KIM2PlayerFlag=true
//                                            KIM3PlayerFlag=true
//                                            KIM4PlayerFlag=false

//                                        ivStatus.setImageResource(R.drawable.tless165)
                                            } else if (TLess165Counter > 10) {
                                                TLess165Counter++
                                                L165Counter = 0
                                                R165Counter = 0
                                                TGreater165Counter = 0
//                                        ivStatus.setImageResource(R.drawable.tless165)
                                                armvoicePlayerFlag = true
                                            }

                                            /** 顯示 Debug 信息 */
//                                    tvAngle.text = getString(R.string.tfe_pe_tv_angle, "左手:$angleLeft 度 右手:$angleRight 度")
//                                    tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $TLess165Counter")
                                        }
/*
                                else if((angleLeft ?: 0 < 165 && angleRight ?:0 >=165)) {
//                                        crosslegCounter = 0
//                                        standardCounter = 0




//                                    if (poseRegister == "左手小於165度") {

                                        L165Counter++
//                                    }
                                    poseRegister = "左手小於165度"

                                    /** 显示当前坐姿状态：脖子前伸 */
                                    if (L165Counter > 50) {

                                        /** 播放提示音 */
                                        if (armvoicePlayerFlag) {
                                            armvoicePlayer.start()
                                        }

                                        armvoicePlayerFlag=false
//                                            standardPlayerFlag = true
//                                            crosslegPlayerFlag = true
//                                            forwardheadPlayerFlag = true
//                                            KIM1PlayerFlag=false
//                                            KIM2PlayerFlag=true
//                                            KIM3PlayerFlag=true
//                                            KIM4PlayerFlag=true

//                                        ivStatus.setImageResource(R.drawable.l165)
                                    } else if (L165Counter > 10) {
                                        L165Counter++
                                        R165Counter  = 0
                                        TGreater165Counter = 0
                                        TLess165Counter = 0
//                                        ivStatus.setImageResource(R.drawable.l165)
                                        armvoicePlayerFlag=true
                                    }

                                    /** 顯示 Debug 信息 */
//                                    tvAngle.text = getString(R.string.tfe_pe_tv_angle, "左手:$angleLeft 度 右手:$angleRight 度")
//                                    tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $L165Counter")
                                }
                                else if((angleLeft ?: 0 >= 165)&&(angleRight ?: 0 < 165)) {
//                                        crosslegCounter = 0
//                                        standardCounter = 0

                                        R165Counter++

                                    poseRegister = "右手小於165度"

                                    /** 顯示當前坐姿狀態：脖子前伸 */
                                    if (R165Counter > 50) {

                                        /** 播放提示音 */
                                        if (armvoicePlayerFlag) {
                                            armvoicePlayer.start()
                                        }

                                        armvoicePlayerFlag=false
//                                            standardPlayerFlag = true
//                                            crosslegPlayerFlag = true
//                                            forwardheadPlayerFlag = true
//                                            KIM1PlayerFlag=true
//                                            KIM2PlayerFlag=false
//                                            KIM3PlayerFlag=true
//                                            KIM4PlayerFlag=true

//                                        ivStatus.setImageResource(R.drawable.r165)
                                    } else if (R165Counter > 10) {
                                        L165Counter  = 0
                                        R165Counter
                                        TGreater165Counter = 0
                                        TLess165Counter = 0
//                                        ivStatus.setImageResource(R.drawable.r165)
                                        armvoicePlayerFlag=true
                                    }

                                    /** 顯示 Debug 信息 */
//                                    tvAngle.text = getString(R.string.tfe_pe_tv_angle, "左手:$angleLeft 度 右手:$angleRight 度")
//                                    tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $R165Counter")
                                }

*/
                                        else if (angleLeft == NULL.toInt() || angleRight == NULL.toInt()) {
//                                    ivStatus.setImageResource(R.drawable.no_target)
                                            tvAngle.text =
                                                getString(R.string.tfe_pe_tv_angle, "未偵測到 ")
                                        }
                                    }

//                                    else  {
//                                        forwardheadCounter = 0
//                                        crosslegCounter = 0
//                                        L165Counter  = 0
//                                        R165Counter  = 0
//                                        TGreater165Counter = 0
//                                        TLess165Counter = 0
//                                        if (poseRegister == "standard") {
//                                            standardCounter++
//                                        }
//                                        poseRegister = "standard"
//                                        ivStatus.setImageResource(R.drawable.no_target)
//                                        /** 顯示當前坐姿狀態：标准 */
//                                        if (standardCounter > 20) {
//
//                                            /** 播放提示音：坐姿标准 */
////                                            if (standardPlayerFlag) {
////                                                standardPlayer.start()
////                                            }
////                                            standardPlayerFlag = false
////                                            crosslegPlayerFlag = true
////                                            forwardheadPlayerFlag = true
////                                            KIM1PlayerFlag=true
////                                            KIM2PlayerFlag=true
////                                            KIM3PlayerFlag=true
////                                            KIM4PlayerFlag=true
//
//                                            // ivStatus.setImageResource(R.drawable.standard)
//                                        }
//
//                                        /** 顯示 Debug 信息 */
//                                        tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $standardCounter")
//                                    }
//                                }
                                    when (sortedLabels[0].first) {
                                        "cpr" -> {
//                                        tvAngle.text = getString(
//                                            R.string.tfe_pe_tv_angle,
//                                            "左手:$angleLeft 度 右手:$angleRight 度"
                                            cprflag = true
                                            cprcount++
                                            uncprcount = 0

                                            if (cprcount == 1 && System.currentTimeMillis() - preTime > 2000) {


//                                            cycleidx++
                                                cycleidx = maxDiffDataList.size + 1
                                                if (csvsave) {//只要csvsave有開得的話就儲存
                                                    writeCycleToCsv()
                                                    writeCycleToCsv2()
                                                }
                                                preTime = System.currentTimeMillis()
                                                willCycle = true

                                            }

//                                        )
//                                        tvAngle.text = getString(R.string.tfe_pe_tv_angle, "${sortedLabels[0].first} ")
                                        }
                                        "uncpr" -> {
                                            cprflag = false
                                            uncprcount++
                                            if (uncprcount > 5) {
                                                cprcount = 0
                                            }


//                                        tvAngle.text = getString(R.string.tfe_pe_tv_angle, "${sortedLabels[0].first} ")
                                        }
//                                        crosslegCounter = 0
//                                        standardCounter = 0
//                                        L165Counter  = 0
//                                        R165Counter  = 0
//                                        TGreater165Counter = 0
//                                        TLess165Counter = 0
//                                        if (poseRegister == "forwardhead") {
//                                            forwardheadCounter++
//                                        }
//                                        poseRegister = "forwardhead"
//
//                                        /** 显示当前坐姿状态：脖子前伸 */
//                                        if (forwardheadCounter > 60) {

                                        /** 播放提示音 */
//                                            if (forwardheadPlayerFlag) {
//                                                forwardheadPlayer.start()
//                                            }
//                                            standardPlayerFlag = true
//                                            crosslegPlayerFlag = true
//                                            forwardheadPlayerFlag = false
//
//                                            ivStatus.setImageResource(R.drawable.forwardhead_confirm)
//                                        } else if (forwardheadCounter > 10) {
//                                            ivStatus.setImageResource(R.drawable.forwardhead_suspect)
//                                        }
//
//                                        /** 显示 Debug 信息 */
//                                        tvDebug.text = getString(R.string.tfe_pe_tv_debug, "${sortedLabels[0].first} $forwardheadCounter")
//                                    }
//                                    "crossleg" -> {
//                                        forwardheadCounter = 0
//                                        standardCounter = 0
//                                        L165Counter  = 0
//                                        R165Counter  = 0
//                                        TGreater165Counter = 0
//                                        TLess165Counter = 0
//                                        if (poseRegister == "crossleg") {
//                                            crosslegCounter++
//                                        }
//                                        poseRegister = "crossleg"
//
                                    }

                                } else {
                                    missingCounter++
                                    if (missingCounter > 30) {
                                        //    ivStatus.setImageResource(R.drawable.no_target)
                                    }
                                    tvCycle.text =
                                        getString(R.string.tfe_pe_tv_cycle, "第 $cycleidx 循環")
//                                tvCycle.text =
//                                    getString(R.string.tfe_pe_tv_cycle, "The ${maxDiffDataList.size}   cycle")

                                    tvDeep.text = getString(R.string.tfe_pe_tv_deep, "未偵測到")
                                    tvFrequency.text =
                                        getString(R.string.tfe_pe_tv_frequency, "未偵測到")
                                    /** 顯示 Debug 信息 */
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

//            spnDevice.adapter = adapter
//            spnDevice.onItemSelectedListener = changeDeviceListener
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_camera_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

//            spnCamera.adapter = adapter
//            spnCamera.onItemSelectedListener = changeCameraListener
        }
        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_model_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

//            spnModel.adapter = adapter
//            spnModel.onItemSelectedListener = changeModelListener
        }

    }

    /** 在程序運行過程中切換運行計算設備*/
    private fun changeDevice(position: Int) {
        val targetDevice = Device.NNAPI/* when (position) {
            0 -> Device.NNAPI
            1 -> Device.GPU
            else -> Device.CPU
//            0 -> Device.CPU
//            1 -> Device.GPU
//            else -> Device.NNAPI
        }*/
        if (device == targetDevice) return
        device = targetDevice
        createPoseEstimator()
    }

    /**在程序運行過程中切換攝像頭 */
    private fun changeCamera(direaction: Int) {
        val targetCamera = Camera.BACK /*when (direaction) {
            0 -> Camera.BACK
//            else -> Camera.FRONT
            1-> Camera.FRONT
            else -> Camera.VIDEO
        }*/
        if (selectedCamera == targetCamera) return
        selectedCamera = targetCamera

        cameraSource?.close()
        cameraSource = null
        openCamera()
    }

    private fun changeModel(direaction: Int) {
        val targetModel = ModelType.Thunder/* when (direaction) {
            0 -> ModelType.Thunder
            else -> ModelType.Lightning
        }*/
        if (model == targetModel) return
        model = targetModel

//        cameraSource?.close()
//        cameraSource = null
        createPoseEstimator()
    }


    private fun createPoseEstimator() {
//        val model=ModelType.Lightning
        val poseDetector = MoveNet.create(this, device, model)
//       tvDebug.text = getString(R.string.tfe_pe_tv_debug, "未偵測到人 $missingCounter")
//        tvModelType.text=getString(R.string.tfe_pe_tv_model, "Movenet  $model")
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
