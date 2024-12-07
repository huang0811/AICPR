package lyi.linyi.posemon

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class  BattleResultActivity : AppCompatActivity() {

    private lateinit var ibBack: ImageButton
    private lateinit var ibRestart: ImageButton
    private lateinit var backgroundImageView: ImageView
    private lateinit var ibHelp: ImageButton
    private lateinit var firestore: FirebaseFirestore

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle_result)
        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()
        initUI()

        // 接收數據
        val result = intent.getStringExtra("result")
        val battleMaxDiffDataList = intent.getParcelableArrayListExtra<BattleMaxDiffData>("maxDiffDataList") ?: ArrayList()

        if (battleMaxDiffDataList != null) {
            // 成功接收到資料，繼續處理
            Log.d("BattleResultActivity", "接收到的資料: $battleMaxDiffDataList")
        } else {
            Log.e("BattleResultActivity", "未能接收到 maxDiffDataList")
        }

        // 根據對戰結果更新 UI
        updateUIBasedOnResult(result)

        // 運算數據
        calculateAndSaveData(result, battleMaxDiffDataList)
    }

    private fun initUI() {
        backgroundImageView = findViewById(R.id.backgroundImage)
        ibBack = findViewById(R.id.ibBack)
        ibRestart = findViewById(R.id.ibRestart)
        ibHelp = findViewById(R.id.ibHelp) // 初始化 ask for help 按鈕

        ibBack.setOnClickListener {
            startActivity(Intent(this, SelectActivity::class.java))
            finish()
        }

        ibRestart.setOnClickListener {
            startActivity(Intent(this, MatchActivity::class.java))
            finish()
        }

        ibHelp.setOnClickListener {
        startActivity(Intent(this, chat_robot::class.java))
        finish()
        }
    }

    private fun calculateAndSaveData(result: String?, maxDiffDataList: ArrayList<BattleMaxDiffData>) {
        if (result == null) {
            Toast.makeText(this, "無法計算，結果數據為空！", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "未登入用戶，無法保存數據！", Toast.LENGTH_SHORT).show()
            return
        }

        // 計算平均數據
        val averageData = calculateAverages(maxDiffDataList)
        val cycles = maxDiffDataList.size + 1

        // 保存數據到 Firestore
        saveBattleDataToFirestore(result, userId, averageData, cycles)
    }

    private fun calculateAverages(maxDiffDataList: ArrayList<BattleMaxDiffData>): Map<String, Float> {
        var totalDepth = 0.0
        var totalFrequency = 0.0
        var totalBothAngle = 0.0
        var completedCycles = 0

        Log.d("Debug", "maxDiffDataList: $maxDiffDataList")

        maxDiffDataList.forEach { data ->
            totalDepth += data.deep
            totalFrequency += data.frequency
            totalBothAngle += data.bothAngle
            if (data.isCycleCompleted) {
                completedCycles++
            }
        }

        val count = maxDiffDataList.size
        val averageDepth = (totalDepth / count).toFloat()
        val averageFrequency = (totalFrequency / count).toFloat()
        val averageAngle = (totalBothAngle / count).toFloat()

        Log.d("Debug", "totalDepth: $totalDepth")
        Log.d("Debug", "totalFrequency: $totalFrequency")
        Log.d("Debug", "totalBothAngle: $totalBothAngle")
        Log.d("Debug", "averageDepth: $averageDepth")
        Log.d("Debug", "averageFrequency: $averageFrequency")
        Log.d("Debug", "averageAngle: $averageAngle")

        return mapOf(
            "totalDepth" to totalDepth.toFloat(),
            "totalFrequency" to totalFrequency.toFloat(),
            "totalBothAngle" to totalBothAngle.toFloat(),
            "averageDepth" to averageDepth,
            "averageFrequency" to averageFrequency,
            "averageAngle" to averageAngle,
            "cycles" to completedCycles.toFloat()
        )
    }

    private fun updateUIBasedOnResult(result: String?) {
        // 根據比賽結果顯示勝利或失敗畫面
        if (result == "win") {
            backgroundImageView.setImageResource(R.drawable.ic_champion)
            // 隱藏 ask for help 按鈕
            ibHelp.visibility = ImageButton.GONE
        } else {
            backgroundImageView.setImageResource(R.drawable.ic_fail)
            // 顯示 ask for help 按鈕
            ibHelp.visibility = ImageButton.VISIBLE
        }
    }

    private fun saveBattleDataToFirestore(
        result: String,
        userId: String,
        averageData: Map<String, Float>,
        cycles: Int
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val battleResult = if (result == "win") "勝利" else "失敗"

        val battleData = mapOf(
            "userID" to userId,
            "timestamp" to timestamp,
            "mode" to "battle",
            "qualificationResult" to battleResult,
            "averageDepth" to averageData["averageDepth"],
            "averageFrequency" to averageData["averageFrequency"],
            "averageAngle" to averageData["averageAngle"],
            "cycles" to cycles
        )

        // 儲存到 Firestore
        val collectionRef = firestore.collection("user_history")
        collectionRef.add(battleData)
            .addOnSuccessListener {
                Log.d("BattleResultActivity", "數據保存成功")
                Toast.makeText(this, "數據已成功保存！", Toast.LENGTH_SHORT).show()
                limitUserHistory(userId) // 限制紀錄數量
            }
            .addOnFailureListener { e ->
                Log.e("BattleResultActivity", "保存數據失敗: ${e.message}")
                Toast.makeText(this, "數據保存失敗，請稍後重試！", Toast.LENGTH_SHORT).show()
            }
    }

    private fun limitUserHistory(userId: String) {
        val collectionRef = firestore.collection("user_history").document(userId).collection("records")
        collectionRef.orderBy("timestamp").get()
            .addOnSuccessListener { documents ->
                if (documents.size() > 50) {
                    val excessDocuments = documents.documents.take(documents.size() - 50)
                    for (document in excessDocuments) {
                        document.reference.delete()
                    }
                    Log.d("BattleResultActivity", "超過 50 筆的紀錄已刪除")
                }
            }
            .addOnFailureListener { e ->
                Log.e("BattleResultActivity", "限制紀錄失敗: ${e.message}")
            }
    }
}