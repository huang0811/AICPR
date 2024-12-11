package lyi.linyi.posemon

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var normalModeButton: ImageButton
    private lateinit var battleModeButton: ImageButton
    private var historyRecords: List<HistoryRecord> = emptyList()
    private var isBattleMode = false  // 用於判斷當前顯示的模式

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // 檢查是否已登入
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        val currentUserId = sharedPref.getString("userID", null)

        if (currentUserId == null) {
            // 未登入，用 Toast 提示，並跳轉到登入頁面
            makeText(this, "請先登入後再查看歷史紀錄！", LENGTH_LONG).show()
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
            finish()
            return
        }

        // 初始化視圖
        normalModeButton = findViewById(R.id.normal_mode)
        battleModeButton = findViewById(R.id.battle_mode)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)

        // 設置按鈕的點擊事件
        normalModeButton.setOnClickListener {
            isBattleMode = false
            lifecycleScope.launch {
                updateMode()
            }
        }

        battleModeButton.setOnClickListener {
            isBattleMode = true
            lifecycleScope.launch {
                updateMode()
            }
        }

        // 初始化 RecyclerView 的 Adapter
        historyAdapter = HistoryAdapter(this, historyRecords) { record ->
            val intent = if (isBattleMode) {
                Intent(this, DetailHistoryBattleActivity::class.java)
            } else {
                Intent(this, DetailHistoryNormalActivity::class.java)
            }
//            intent.putExtra("record_id", record.date)
            intent.putExtra("document_id", record.documentId) // 传递 documentId
            intent.putExtra("timestamp", record.timestamp)
            intent.putExtra("detailed_data", ArrayList(record.detailedData))
            startActivity(intent)
        }
        historyRecyclerView.adapter = historyAdapter

        // 加載歷史紀錄數據並初始化 Adapter
        lifecycleScope.launch {
            historyRecords = loadHistoryRecords()
            println("History Records Loaded: $historyRecords") // Debug log to check data loading
            historyAdapter.updateData(historyRecords)
        }

        // 綁定返回主選單的按鈕
        val btn_back = findViewById<ImageButton>(R.id.btn_back)
        btn_back.setOnClickListener {
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // 更新模式顯示和按鈕透明度
    private fun updateMode() {
        if (isBattleMode) {
            battleModeButton.alpha = 0.5f   // Battle Mode 變灰
            normalModeButton.alpha = 1f     // Normal Mode 恢復正常
        } else {
            normalModeButton.alpha = 0.5f  // Normal Mode 變灰
            battleModeButton.alpha = 1f    // Battle Mode 恢復正常
        }

        // 更新歷史紀錄數據
        lifecycleScope.launch {
            historyRecords = loadHistoryRecords()
            historyAdapter.updateData(historyRecords)
        }
    }

    // 加載模擬數據的方法，根據模式返回不同的數據
    private suspend fun loadHistoryRecords(): List<HistoryRecord> {
        val dynamicData = fetchRecordsFromDatabase(isBattleMode)
        println("Loaded data: $dynamicData") // 添加调试打印

        // 如果没有操作记录则返回空列表
        if (dynamicData.isEmpty()) return emptyList()

        // 根據 timestamp 排序數據，確保最新的紀錄排在最前面
        val sortedData = dynamicData.sortedBy { it["timestamp"] as String }

        // 生成日期、次序和结果的描述
        val recordCounter = mutableMapOf<String, Int>()
        val records = sortedData.mapNotNull { recordData ->
            val documentId = recordData["documentId"] as? String ?: "Unknown"
            val timestamp = recordData["timestamp"] as? String ?: "Unknown"
            val date = timestamp.split(" ")[0]  // 提取日期部分
            val result = recordData["qualificationResult"] as? String ?: "Unknown"
            val detailedData = recordData["detailedData"] as? List<Map<String, Any>> ?: emptyList()

            val order = (recordCounter[date] ?: 0) + 1
            recordCounter[date] = order

            val formattedDate = date.replace("-", "") // 移除日期中的橫槓
            HistoryRecord(
                documentId = documentId,
                date = date,
                result = result,
                description = "${formattedDate}_$order", // 显示日期 + 次序
                detailedData = detailedData,
                timestamp = timestamp
            )
        }
        // 將資料排序為日期從近到遠，並在每個日期內次序從大到小
        return records.sortedWith(compareByDescending<HistoryRecord> { it.date }
            .thenByDescending { it.description })
    }

    private suspend fun fetchRecordsFromDatabase(isBattleMode: Boolean): List<Map<String, Any>> {
        val firestore = FirebaseFirestore.getInstance()
        val collectionRef = firestore.collection("user_history")
        val records = mutableListOf<Map<String, Any>>()

        // 从 SharedPreferences 获取当前用户的 ID
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        val currentUserId = sharedPref.getString("userID", "Unknown") ?: "Unknown"

        try {
            // 查询模式，分为一般模式和对战模式，并根据用户 ID 过滤
            val querySnapshot: QuerySnapshot = if (isBattleMode) {
                collectionRef.whereEqualTo("mode", "battle")
                    .whereEqualTo("userID", currentUserId)
                    .get().await()
            } else {
                collectionRef.whereEqualTo("mode", "normal")
                    .whereEqualTo("userID", currentUserId)
                    .get().await()
            }

            // 将查询结果转换成 Map
            for (document in querySnapshot) {
                val recordData = mutableMapOf<String, Any>()
                val documentId = document.id
                val date = document.get("timestamp")?.toString() ?: "Unknown" // 保持原有的键名
                val result = document.getString("qualificationResult") ?: "Unknown"
                val detailedData = document.get("detailedData") as? List<Map<String, Any>> ?: emptyList()
                println("Fetched record: date=$date, result=$result, detailedData=$detailedData")  // 调试信息

                recordData["documentId"] = documentId
                recordData["timestamp"] = date
                recordData["qualificationResult"] = result
                recordData["detailedData"] = detailedData
                records.add(recordData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return records
    }
}

// 定義適配器類別，並設置點擊事件
class HistoryAdapter(
    private val context: Context,
    private var historyRecords: List<HistoryRecord>,
    private val itemClickListener: (HistoryRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // 更新數據並刷新列表
    fun updateData(newRecords: List<HistoryRecord>) {
        historyRecords = newRecords
        notifyDataSetChanged()
    }

    // 創建 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    // 綁定數據到 ViewHolder
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = historyRecords[position]
        holder.historyDate.text = record.description
        holder.historyResult.text = record.result

        if (record.result == "失敗" || record.result == "不合格") {
            holder.historyResult.setTextColor(Color.parseColor("#e63c3c"))
        } else {
            holder.historyResult.setTextColor(Color.parseColor("#1279c6"))
        }
        println("Binding record: ${record.description} - ${record.result}") // Debug log

        holder.itemView.setOnClickListener { itemClickListener(record) }
    }

    // 返回數據項目數量
    override fun getItemCount(): Int {
        return historyRecords.size
    }

    // 定義 ViewHolder 內部類
    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val historyDate: TextView = itemView.findViewById(R.id.historyDate)
        val historyResult: TextView = itemView.findViewById(R.id.historyResult)
    }
}

// 定義歷史紀錄的數據類
data class HistoryRecord(
    val documentId: String,
    val date: String,
    val result: String,
    val description: String,
    val detailedData: List<Map<String, Any>> = emptyList(),
    val timestamp: String // 原始時間戳
)
