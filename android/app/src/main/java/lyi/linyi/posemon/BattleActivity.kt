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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

class BattleActivity : AppCompatActivity() ,Player.Listener {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
        const val REQUEST_VIDEO_FILE = 1
    }

    // View 相關變數
    private lateinit var surfaceView: SurfaceView
    private lateinit var btHome: ImageButton
    private lateinit var tvTimer: TextView
    private lateinit var tvFPS: TextView
    private lateinit var tvCycle: TextView
    private lateinit var tvDeep: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvAngle: TextView
    private lateinit var player1: ImageView
    private lateinit var player2: ImageView
    private lateinit var player1name: TextView
    private lateinit var player2name: TextView
    // 計數器和標誌位
    private var missingCounter = 0
    private var L165Counter = 0
    private var R165Counter = 0
    private var TGreater165Counter = 0
    private var TLess165Counter = 0
    private var prefrequency = 0
    private var predeep = 0.0f
    private var count = 0
    private var flagwait = false
    private var preTime = System.currentTimeMillis()
    private var cycleidx = 0
    private var cprflag = true
    private var cprcount = 0
    private var uncprcount = 0
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
    private var correctCount = 0
    private var maxTranslationX = 0f

    // CSV 和文件相關變數
    private lateinit var bufferedWriter: BufferedWriter
    private val dateFormat = SimpleDateFormat("MMddHHmmss")
    private val timestamp = dateFormat.format(Date())

    // 數據和列表
    val csvsave = false  // 是否最後要存 csv 檔
    val windowSize = 2
    val timeDiffs = ArrayDeque<Int>(windowSize)
    val wristYDiffs = ArrayDeque<Float>(windowSize)
    val dataArrayList = ArrayList<CsvData>()
    val dataArrayList_3 = ArrayList<CsvData>()
    val maxDiffDataList = ArrayList<MaxDiffData>()
    val timer = Timer()

    // 媒體播放器和攝像頭相關變數
    private lateinit var player: ExoPlayer
    private var beatPlayer: MediaPlayer? = null
    private lateinit var dataSourceFactory: DataSource.Factory
    private var cameraSource: CameraSource? = null
    private lateinit var getContent: ActivityResultLauncher<Intent>
    private var isClassifyPose = true

    // Firebase 相關變數
    private lateinit var database: FirebaseDatabase
    private lateinit var matchRef: DatabaseReference
    private var userId: String? = null
    private var isPlayer1: Boolean = false
    private var isAiOpponent = false
    private var aiOpponentDataList = ArrayList<MaxDiffData>()
    private var playerName: String? = null
    private var opponentName: String? = null

    // Handler 和計時器相關變數
    private var seconds = 0
    private val handler = Handler()
    private lateinit var runnable: Runnable

    // 預設計算設備和模型
    private var device = Device.NNAPI
    private var selectedCamera = Camera.BACK
    private var model = ModelType.Thunder

    // 歷史姿態寄存器
    private var poseRegister = "standard"

    val concatenatingMediaSource = ConcatenatingMediaSource()


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

    data class PlayerStatus(
        val deep: Float = 0f,
        val frequency: Int = 0,
        val bothAngle: Int = 0,
        val cycle: Int = 0
    )

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle)

        // 初始化 Firebase
        database = FirebaseDatabase.getInstance()
        userId = getUserId()

        // 獲取從 MatchActivity 傳遞來的參數
        isPlayer1 = intent.getBooleanExtra("isPlayer1", true)
        isAiOpponent = intent.getBooleanExtra("isAiOpponent", false)
        playerName = intent.getStringExtra("playerName") ?: "Player"
        opponentName = intent.getStringExtra("opponentName") ?: "Opponent"
        val roomId = intent.getStringExtra("roomId")  // 獲取房間ID

        player1 = findViewById(R.id.player1)
        player2 = findViewById(R.id.player2)
        player1name = findViewById(R.id.player1name)
        player2name = findViewById(R.id.player2name)

        // 根據角色顯示名稱
        player1name.text = if (isPlayer1) playerName else opponentName
        player2name.text = if (isPlayer1) opponentName else playerName

        // 初始化 Firebase 參照
        if (roomId != null) {
            matchRef = database.getReference("matches").child(roomId)

            // 檢查房間中的玩家狀態
            val opponentPath = if (isPlayer1) "player2" else "player1"
            matchRef.child("playerStatus").child(opponentPath).addValueEventListener(opponentStatusListener)
        } else {
            // 處理錯誤情況，例如顯示錯誤訊息或返回主頁
            Toast.makeText(this, "房間ID無效，無法進行對戰", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 設置 maxTranslationX 為螢幕寬度
        val displayMetrics = resources.displayMetrics
        maxTranslationX = displayMetrics.widthPixels.toFloat() // 設為螢幕寬度

        // 根據參數設定對手類型
        if (isAiOpponent) {
            initializeAndSimulateAiOpponent() // 初始化並模擬 AI 對手
        }

        // 監聽對手狀態變化
        val opponentPath = if (isPlayer1) "player2" else "player1"
        matchRef.child("playerStatus").child(opponentPath).addValueEventListener(opponentStatusListener)

        /**程序運行時保持畫面常亮 */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        btHome = findViewById(R.id.btHome)

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

        tvCycle=findViewById(R.id.tvCycle)
        /** 用来顯示 按壓深度 信息 */
        tvDeep = findViewById(R.id.tvDeep)
        /** 用来顯示 按壓頻率 信息 */
        tvFrequency = findViewById(R.id.tvFrequency)
        /** 用来顯示 角度 信息 */
        tvAngle = findViewById(R.id.tvAngle)

        /**用來顯示當前坐姿狀態 */
        //ivStatus = findViewById(R.id.ivStatus)

        tvFPS = findViewById(R.id.tvFps)
        surfaceView = findViewById(R.id.surfaceView)

        btHome.setOnClickListener {
            // 點擊按鈕時，啟動 MainActivity2
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
            beaterstop()

            // 關閉當前的 MainActivity
            finish()
        }

        val intent = Intent(this, BattleResultActivity::class.java)

        val fastwarnPlayer= MediaPlayer.create(this, R.raw.fastvoice)
        var fastwarnPlayerFlag=true
        var fastCounter=0
        val slowwarnPlayer= MediaPlayer.create(this, R.raw.slowvoice)
        var slowwarnPlayerFlag=true
        var slowCounter=0
        val shallowPlayer= MediaPlayer.create(this, R.raw.shallowvoice)
        var shallowPlayerFlag=true
        val timer = timer(period = 400) {
            val maxDiff = dataArrayList.maxByOrNull { it.deep }

            if(maxDiffDataList.size>=5 && uncprcount>5 || player1.translationX == maxTranslationX.toFloat()){
                // 創建 Intent 將列表傳遞到 ResultActivity
                intent.putParcelableArrayListExtra("maxDiffDataList", maxDiffDataList)
                intent.putExtra("EXTRA_TIME", seconds)
                startActivity(intent)
                beaterstop()

                uncprcount=0
                // 關閉當前的 MainActivity
                finish()
            }

            if (maxDiff != null) {
                if (csvsave){//只要csvsave為true，就進行儲存
                    writeToCsv(
                        maxDiff.deep,
                        maxDiff.frequency,
                        maxDiff.leftAngle,
                        maxDiff.rightAngle
                    ) // 將最大的數據寫入 CSV 檔案
                }
                runOnUiThread{
                    // 如果有最大的數據，則顯示在 TextView 中
                    if (maxDiff != null) {

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
                    val frequency=maxDiff.frequency
                    if(maxDiff.deep<3 &&shallowPlayerFlag==true){
                        shallowPlayer.start()
                        shallowPlayerFlag=false
                    }else{
                        shallowPlayerFlag=true

                        if(frequency>120){
                            slowCounter++
                            if(slowCounter>3&&slowwarnPlayerFlag==true) {
                                slowwarnPlayer.start()
                                slowwarnPlayerFlag = false
                                fastCounter=0
                            }else{
                                slowwarnPlayerFlag = true
                            }

                        }else if(frequency<100){
                            fastCounter++
                            if(fastCounter>3&&fastwarnPlayerFlag==true) {
                                fastwarnPlayer.start()
                                fastwarnPlayerFlag = false
                                slowCounter=0
                            }else{
                                fastwarnPlayerFlag=true
                            }
                        }else{
                            if(slowCounter>3){
                                slowCounter=0
                            }
                            if(fastCounter>3){
                                fastCounter=0
                            }

                            slowwarnPlayerFlag=true
                            fastwarnPlayerFlag=true
                        }
                    }
                }
                // 更新 TextView 顯示

            }
        }, 0, 400) // 開始的延遲時間為 0，間隔為 500 毫秒

        if(csvsave) {  //進行是否建立檔案
            csvread()

            csvread2()
        }
        setupPlayer()
        dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoPlayerDemo"))


        getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val uri = data?.data
                val fileuri=getFilePathFromContentUri(this,uri!!)
                val fileUri = Uri.parse("file://$fileuri")
                // 在这里处理所选视频的Uri

                if (fileuri != null) {
                    Toast.makeText(this, fileUri.toString(), Toast.LENGTH_SHORT).show()
                    // 处理所选视频的Uri
                    val mediaSource2= ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(fileUri)
                    concatenatingMediaSource.addMediaSource(0,mediaSource2)
                    player.setMediaSource(concatenatingMediaSource)
                    player.prepare()
                }
            }
        }

        initSpinner()
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }
    }

    private fun getUserId(): String? {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.uid
    }

    // 定義對手狀態監聽器
    private val opponentStatusListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val opponentData = snapshot.getValue(PlayerStatus::class.java)
            if (opponentData != null) {
                val opponentProgress = calculateProgress(opponentData)
                runOnUiThread {
                    player2.translationX += opponentProgress // 更新對手位置
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            // 錯誤處理
        }
    }

    /** 計算對手的位移量 */
    private fun calculateProgress(playerData: PlayerStatus): Float {
        var matchCount = 0

        // 判斷深度是否符合標準
        if (playerData.deep in 5.0..6.0) matchCount++

        // 判斷頻率是否符合標準
        if (playerData.frequency in 100..120) matchCount++

        // 判斷角度是否符合標準
        if (playerData.bothAngle > 165) matchCount++

        // 計算單位距離（與玩家一致）
        val unitDistance = maxTranslationX / 128

        // 根據符合條件的數量來計算前進距離
        return when (matchCount) {
            3 -> 2 * unitDistance // 三個條件都符合，前進兩個單位距離
            2 -> 1 * unitDistance // 兩個條件符合，前進一個單位距離
            else -> 0f // 其他情況不前進
        }
    }


    // 生成和模擬 AI 對手
    private val aiHandler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null

    private fun initializeAndSimulateAiOpponent() {
        aiRunnable = object : Runnable {
            override fun run() {
                val random = Random()
                val deep = 4.0 + random.nextDouble() * 3.0 // 深度在 4~7 公分間
                val frequency = 90 + random.nextInt(41) // 頻率在 90~130 次/分鐘
                val bothAngle = 155 + random.nextInt(26) // 雙手角度在 155~180 度間

                // 使用通用的 calculateProgress 函數
                val aiProgress = calculateProgress(PlayerStatus(deep.toFloat(), frequency, bothAngle))

                runOnUiThread {
                    player2.translationX += aiProgress // 更新 AI 的位置

                    // 檢查是否達到勝利條件
                    if (player2.translationX >= maxTranslationX) {
                        endBattle(isWinner = false) // AI 贏了
                    } else {
                        aiHandler.postDelayed(this, 1000) // 每秒執行一次
                    }
                }
            }
        }

        aiHandler.post(aiRunnable!!) // 開始執行
    }

    override fun onStart() {
        super.onStart()
        openCamera()
    }

    suspend fun waitSecond() {
        while (true){
            delay(200)
            flagwait=false
            break
        }
    }

    fun beater() {
        // 設定每個時間間隔執行的任務
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

    fun beaterstop(){
        beatPlayer?.release()
        beatPlayer = null

    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()

        setupPlayer()
        beater()
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

        // 移除對戰計時的回調
        handler.removeCallbacks(runnable)
        timer.cancel()

        // 移除AI模擬的回調
        aiRunnable?.let { aiHandler.removeCallbacks(it) }

        // 停止播放器
        player.release()

        // 停止節拍聲音
        beaterstop()

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

        player.addListener(this)
        val uri1 = Uri.parse("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")

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

    fun csvread() {//進行csv的創建初始化
        val csvHeader = arrayOf("deep", "frequency", "Langle", "Rangle","Timestamp")
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cpr_$timestamp.csv")//開檔案放到手機內存的Documents裡，讀cpr.csv檔

        if (!file.exists()) {//如果沒檔案
            file.createNewFile()//就創建cpr.csv檔
        }

        val fileWriter = FileWriter(file,false)//false是從頭開始寫入資料，true為接下去寫入資料
        bufferedWriter = BufferedWriter(fileWriter)

        bufferedWriter.append(csvHeader.joinToString(","))
        bufferedWriter.newLine()

        // 做完後別忘了要關閉 BufferedWriter 與 FileWriter
        bufferedWriter.flush()
        bufferedWriter.close()
        fileWriter.close()
    }

    fun writeToCsv(deep: Float, frequency: Int, leftAngle: Int, rightAngle: Int) {
        try {
            // 打開 CSV 文件，創建 FileWriter 和 BufferedWriter
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cpr_$timestamp.csv")//開檔案放到手機內存的Documents裡，讀cpr.csv檔
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

    fun writeCycleToCsv() {
        try {
            // 打開 CSV 文件，創建 FileWriter 和 BufferedWriter
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cpr_$timestamp.csv")//開檔案放到手機內存的Documents裡，讀cpr.csv檔
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
        val csvHeader = arrayOf("deep", "frequency", "Langle", "Rangle","Timestamp")
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cpr_t_$timestamp.csv")//開檔案放到手機內存的Documents裡，讀cpr.csv檔

        if (!file.exists()) {//如果沒檔案
            file.createNewFile()//就創建cpr.csv檔
        }

        val fileWriter = FileWriter(file,false)//false是從頭開始寫入資料，true為接下去寫入資料
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
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cpr_t_$timestamp.csv")//開檔案放到手機內存的Documents裡，讀cpr.csv檔
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
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "cpr_t_$timestamp.csv")//開檔案放到手機內存的Documents裡，讀cpr.csv檔
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
        beatPlayer= MediaPlayer.create(this, R.raw.beat)
        /** 音频播放 */
        val armvoicePlayer= MediaPlayer.create(this, R.raw.armvoice)
        var armvoicePlayerFlag=true

        fun leftKeypoints(

        ): Int? {
            val pointA=VisualizationUtils.leftpointAA
            val pointB=VisualizationUtils.leftpointBB
            val pointC=VisualizationUtils.leftpointCC

            val pointAB = Math.sqrt(
                Math.pow((pointA.x - pointB.x).toDouble(), 2.0) + Math.pow((pointA.y - pointB.y).toDouble(), 2.0)
            )
            val pointBC = Math.sqrt(
                Math.pow((pointB.x - pointC.x).toDouble(), 2.0) + Math.pow((pointB.y - pointC.y).toDouble(), 2.0)
            )
            val pointAC = Math.sqrt(
                Math.pow((pointA.x - pointC.x).toDouble(), 2.0) + Math.pow((pointA.y - pointC.y).toDouble(), 2.0)
            )

            if((pointAB+pointBC)<pointAC||(pointBC+pointAC)<pointAB|| (pointAB+pointAC)<pointBC
                || Math.abs(pointAB-pointBC)>pointAC ||Math.abs(pointBC-pointAC)>pointAB ||Math.abs(pointAB-pointAC)>pointBC) {
                return NULL.toInt()
            }
            else{
                val angle = Math.toDegrees(
                    Math.acos(
                        ((Math.pow(pointBC, 2.0) + Math.pow(pointAB, 2.0) - Math.pow(pointAC, 2.0)) / (2 * pointBC * pointAB))
                    )
                ).toInt()
                return angle}
        }

        fun rightKeypoints(

        ): Int? {
            val pointA=VisualizationUtils.rightpointAA
            val pointB=VisualizationUtils.rightpointBB
            val pointC=VisualizationUtils.rightpointCC

            val pointAB = Math.sqrt(
                Math.pow((pointA.x - pointB.x).toDouble(), 2.0) + Math.pow((pointA.y - pointB.y).toDouble(), 2.0)
            )
            val pointBC = Math.sqrt(
                Math.pow((pointB.x - pointC.x).toDouble(), 2.0) + Math.pow((pointB.y - pointC.y).toDouble(), 2.0)
            )
            val pointAC = Math.sqrt(
                Math.pow((pointA.x - pointC.x).toDouble(), 2.0) + Math.pow((pointA.y - pointC.y).toDouble(), 2.0)
            )

            if((pointAB+pointBC)<pointAC||(pointBC+pointAC)<pointAB|| (pointAB+pointAC)<pointBC
                || Math.abs(pointAB-pointBC)>pointAC ||Math.abs(pointBC-pointAC)>pointAB ||Math.abs(pointAB-pointAC)>pointBC) {
                return NULL
            }
            else{
                val angle = Math.toDegrees(
                    Math.acos(
                        ((Math.pow(pointBC, 2.0) + Math.pow(pointAB, 2.0) - Math.pow(pointAC, 2.0)) / (2 * pointBC * pointAB))
                    )
                ).toInt()
                return angle}
        }

        suspend fun frequencyPress(callback: (Int,Float?) -> Unit) {
            var wristY1 =0f
            var wristY2 = 0f
            var startTime: Long? = null
            var endTime: Long? = null
            var initialHeight: Float? = null
            var state = 0 // 0: 等待初始高度，1: 第一次按壓，2: 第二次按壓
            var isReadyForSecondCycle = false
            var outrange=false

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
                            } else if (wristY < wristY2 - 5 && isReadyForSecondCycle && Math.abs(wristY1 - wristY2) < 5 ) {//&& Math.abs(wristY - wristY2) > 5--------------
                                endTime = System.currentTimeMillis()
                                break
                            }else if(wristY1!=0f&&wristY2!=0f&&Math.abs(wristY1 - wristY2)>70){//防止y1和y2差距過大而建置的早停機制
                                endTime = System.currentTimeMillis()
                                outrange=true
                                break
                            }
                        }
                    }
                    delay(50)
                    // 返回时间差
                }
                (endTime!! - startTime!!).toInt()
            }

            val rate=3.9 //距離安妮85公分(統計版)
            if (timediff<500&&timediff>300&&!outrange) {
                callback((500-(500-timediff)*0.1).toInt(), String.format("%.2f", (wristY2 - initialHeight!!)/ rate).toFloat())//
            }

            if (timediff>=500&&timediff<=600&&!outrange) {
                callback(timediff, String.format("%.2f", (wristY2 - initialHeight!!)/ rate).toFloat())//
            }

            if (timediff>600&&timediff<5000&&!outrange) {
                callback((600+timediff*0.01).toInt(), String.format("%.2f", (wristY2 - initialHeight!!)/ rate).toFloat())//
            }

            else {//反之將此函式停掉
                return
            }
        }

        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource =
                    CameraSource(surfaceView, selectedCamera, object : CameraSource.CameraSourceListener {
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
                                val angleLeft = leftKeypoints()
                                val angleRight= rightKeypoints()

                                lifecycleScope.launch{
                                    if(cprflag) {
                                        frequencyPress { timediff,wristYDiff ->
                                            var olddiff=timeDiffs.average()

                                            if(prefrequency!=timediff) {
                                                timeDiffs.addLast(timediff)
                                                prefrequency=timediff
                                            }

                                            if(predeep!=wristYDiff&& wristYDiff!! <7f&&wristYDiff>2f){
                                                wristYDiffs.addLast(wristYDiff!!)
                                                predeep= wristYDiff
                                            }

                                            if(timeDiffs.size>15&& Math.abs(timeDiffs.average()- olddiff!!)>100) {//判別是否有離群值，有離群值就去除掉
                                                timeDiffs.removeLast()
                                            }

                                            if (timeDiffs.size > 2) {
                                                timeDiffs.removeFirst()
                                            }

                                            if (wristYDiffs.size > windowSize) {
                                                wristYDiffs.removeFirst()
                                            }

                                            // 計算平均頻率與深度
                                            val averageTimediff = timeDiffs.average()
                                            val averagewristYDiff = String.format("%.2f", wristYDiffs.average().toFloat())
                                            val frequency = 60f / (averageTimediff / 1000f)
                                            var matchCount = 0

                                            val csvData = CsvData(averagewristYDiff.toFloat(), frequency.toInt(), angleLeft!!, angleRight!!,)
                                            dataArrayList.add(csvData)
                                            dataArrayList_3.add(csvData)
                                            totalcount++

                                            // 判斷是否符合CPR條件
                                            // 計算頻率
                                            if (csvData.frequency in 100..120) {
                                                frequencyCount++
                                            }

                                            // 計算深度
                                            if (csvData.deep in 5.0..6.0) {
                                                deepCount++
                                            }

                                            // 計算角度（左手角度和右手角度的平均值）
                                            val averageAngle = (csvData.leftAngle + csvData.rightAngle) / 2
                                            if (averageAngle > 165) {
                                                leftAngleCount++
                                                rightAngleCount++
                                            }
//                                            // 判斷是否符合CPR條件
//                                            if (csvData.frequency in 100..120 && csvData.deep in 5.0..6.0 &&
//                                                csvData.leftAngle > 165 && csvData.rightAngle > 165) {
//                                                correctCount++
//                                                incrementPlayerPosition() // 更新進度
//                                                updatePlayerStatusInFirebase(isPlayer1) // 更新Firebase數據
//                                            }

                                            // 根據符合條件的數量移動角色
                                            var playerProgress = 0f
                                            // 計算每單位前進距離
                                            val unitDistance = maxTranslationX / 128

                                            // 三個部分都在標準內
                                            if (deepCount > 0 && frequencyCount > 0 && (leftAngleCount > 0 && rightAngleCount > 0)) {
                                                playerProgress = 2* unitDistance // 前進兩格
                                            }
                                            // 只有兩個部分在標準內
                                            else if ((deepCount > 0 && frequencyCount > 0) ||
                                                (deepCount > 0 && (leftAngleCount > 0 || rightAngleCount > 0)) ||
                                                (frequencyCount > 0 && (leftAngleCount > 0 || rightAngleCount > 0))) {
                                                playerProgress = unitDistance // 前進一格
                                            }

                                            // 更新玩家位置
                                            if (playerProgress > 0) {
                                                incrementPlayerPosition(playerProgress)
                                            }

                                            if (dataArrayList_3.size > 2) {
                                                val maxDiff_3 = dataArrayList_3.maxByOrNull { it.deep }

                                                if (maxDiff_3 != null) {
                                                    totalDeep+=maxDiff_3.deep
                                                    totalFrequency+= maxDiff_3.frequency
                                                    totalLeftAngle+=maxDiff_3.leftAngle
                                                    totalRightAngle+= maxDiff_3.rightAngle
                                                    totalBothAngle+=(maxDiff_3.leftAngle+maxDiff_3.rightAngle)/2

                                                    if(csvsave) {
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
                                    }
                                    else {
                                        tvDeep.text =getString(R.string.tfe_pe_tv_deep,"等待下一個循環")
                                        tvFrequency.text = getString(R.string.tfe_pe_tv_frequency,"等待下一個循環")
                                        tvAngle.text = getString(R.string.tfe_pe_tv_angle,"等待下一個循環")

                                        if(willCycle){
//                                            if(maxDiffDataList.size<5) {//&&totalDeep!=0.0
                                                if(totalcount!=0) {
                                                    maxDiffDataList.add(
                                                        MaxDiffData(   //計算所有值的平均
                                                            (totalDeep.toFloat() / totalcount) ,
                                                            (totalBothAngle.toFloat() / totalcount) ,
                                                            (totalRightAngle.toFloat() / totalcount) ,
                                                            true
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
                                                    willCycle = false
//                                                }
                                            }
                                        }
                                    }
                                }
                                // 判斷是否進入 CPR 或 non-CPR 模式
                                val sortedLabels = poseLabels!!.sortedByDescending { it.second }
                                if(cprflag) {
                                    missingCounter = 0

                                    if((angleLeft ?: 0 >= 165) && (angleRight ?: 0 >= 165)) {
                                        TGreater165Counter ++
                                        poseRegister = "雙手大於165度"

                                        if (TGreater165Counter  > 50) {

                                        } else if (TGreater165Counter  > 10) {
                                            TGreater165Counter ++
                                            L165Counter  = 0
                                            R165Counter = 0

                                            TLess165Counter = 0
                                        }
                                    }
                                    else if ((angleLeft ?: 0 < 165) || (angleRight ?: 0 < 165)){
                                        TLess165Counter++

                                        poseRegister = "雙手小於165度"

                                        /** 顯示當前坐姿狀態：脖子前伸 */
                                        if(TLess165Counter>100){
                                            TLess165Counter=0
                                        }
                                        else if (TLess165Counter > 50) {

                                            /** 播放提示音 */
                                            if (armvoicePlayerFlag) {
                                                armvoicePlayer.start()
                                            }
                                            armvoicePlayerFlag=false
                                        } else if (TLess165Counter > 10) {
                                            TLess165Counter++
                                            L165Counter  = 0
                                            R165Counter = 0
                                            TGreater165Counter = 0
                                            armvoicePlayerFlag=true
                                        }
                                    }
                                    else if(angleLeft== NULL.toInt()|| angleRight == NULL.toInt()) {
                                        tvAngle.text = getString(R.string.tfe_pe_tv_angle, "未偵測到 ")
                                    }
                                }
                                when (sortedLabels[0].first) {
                                    "cpr" -> {
                                        cprflag=true
                                        cprcount++
                                        uncprcount=0

                                        if(cprcount==1&&System.currentTimeMillis()-preTime>2000) {
                                            cycleidx = maxDiffDataList.size + 1
                                            if (csvsave) {//只要csvsave有開得的話就儲存
                                                writeCycleToCsv()
                                                writeCycleToCsv2()
                                            }
                                            preTime=System.currentTimeMillis()
                                            willCycle=true
                                        }
                                    }
                                    "uncpr" -> {
                                        cprflag=false
                                        uncprcount++
                                        if(uncprcount>5) {
                                            cprcount = 0
                                        }
                                    }
                                }
                            }
                            else {
                                missingCounter++
                                if (missingCounter > 30) {
                                }
                                /** 顯示 Debug 信息 */
                                tvCycle.text = getString(R.string.tfe_pe_tv_cycle, "第 $cycleidx 循環")
                                tvDeep.text = getString(R.string.tfe_pe_tv_deep, "未偵測到")
                                tvFrequency.text = getString(R.string.tfe_pe_tv_frequency, "未偵測到" )
                                tvAngle.text = getString(R.string.tfe_pe_tv_angle, "未偵測到")
                            }
                        }

                        private fun incrementPlayerPosition(playerProgress: Float) {
                            // 根據角色更新位置
                            if (isPlayer1) {
                                player1.translationX += playerProgress
                            } else {
                                player2.translationX += playerProgress
                            }

                            // 更新Firebase中的進度
                            updatePlayerStatusInFirebase(isPlayer1)
                            checkWinCondition()
                        }

                        /** 更新玩家狀態至 Firebase */
                        private fun updatePlayerStatusInFirebase(isPlayer1: Boolean) {
                            val isValid = (totalDeep in 5.0..6.0 && totalFrequency in 100..120 && totalBothAngle > 165)

                            val playerData = mapOf(
                                "deep" to totalDeep / totalcount,
                                "frequency" to totalFrequency / totalcount,
                                "bothAngle" to totalBothAngle / totalcount,
                                "isValid" to isValid
                            )

                            val playerPath = if (isPlayer1) "player1" else "player2"
                            matchRef.child("playerStatus").child(playerPath).setValue(playerData)
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

    private fun checkWinCondition() {
        // 如果自己的角色達到終點
        if ((isPlayer1 && player1.translationX >= maxTranslationX) ||
            (!isPlayer1 && player2.translationX >= maxTranslationX)) {
            endBattle(isWinner = true) // 自己的角色贏
            return
        }

        // 如果對方的角色達到終點
        if ((isPlayer1 && player2.translationX >= maxTranslationX) ||
            (!isPlayer1 && player1.translationX >= maxTranslationX)) {
            endBattle(isWinner = false) // 對方的角色贏
            return
        }
    }

    private fun endBattle(isWinner: Boolean) {
        // 停止所有背景操作
        handler.removeCallbacks(runnable)
        timer.cancel()
        player.release()
        beaterstop()

        // 避免重複跳轉
        if (isFinishing) return

        // 創建跳轉到 BattleResultActivity 的 Intent
        val resultIntent = Intent(this, BattleResultActivity::class.java)

        // 傳遞對戰結果
        resultIntent.putExtra("result", if (isWinner) "win" else "lose")
        resultIntent.putExtra("averageDeep", totalDeep / totalcount)
        resultIntent.putExtra("averageFrequency", totalFrequency / totalcount)
        resultIntent.putExtra("averageBothAngle", totalBothAngle / totalcount)
        resultIntent.putExtra("cycles", maxDiffDataList.size)

        // 跳轉到結果頁面
        try {
            startActivity(resultIntent)
            finish()
        } catch (e: Exception) {
            Log.e("BattleActivity", "跳轉到 BattleResultActivity 時發生異常: ${e.message}")
            Toast.makeText(this, "發生錯誤，無法跳轉到對戰結果頁面", Toast.LENGTH_SHORT).show()
        }

        // 保存對戰記錄
        saveHistoryRecord(isWinner)

        // 移除Firebase對戰數據
        if (::matchRef.isInitialized) {
            matchRef.removeValue()
        }
    }

    private fun saveHistoryRecord(isWinner: Boolean) {
        val historyRef = database.getReference("history").child(userId!!)
        val record = mapOf(
            "averageDeep" to (totalDeep / totalcount),
            "averageFrequency" to (totalFrequency / totalcount),
            "averageBothAngle" to (totalBothAngle / totalcount),
            "cycles" to maxDiffDataList.size,
            "result" to if (isWinner) "win" else "lose"
        )
        historyRef.push().setValue(record)
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
        val targetDevice =Device.NNAPI
        if (device == targetDevice) return
        device = targetDevice
        createPoseEstimator()
    }

    /**在程序運行過程中切換攝像頭 */
    private fun changeCamera(direaction: Int) {
        val targetCamera =Camera.BACK
        if (selectedCamera == targetCamera) return
        selectedCamera = targetCamera

        cameraSource?.close()
        cameraSource = null
        openCamera()
    }

    private fun changeModel(direaction: Int) {
        val targetModel =ModelType.Thunder
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