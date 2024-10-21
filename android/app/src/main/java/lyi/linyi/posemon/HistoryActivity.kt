package lyi.linyi.posemon

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyRecords: List<HistoryRecord>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // 初始化 RecyclerView
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)

        // 加載模擬數據
        historyRecords = loadHistoryRecords()

        // 初始化 Adapter，並設置點擊事件
        historyAdapter = HistoryAdapter(this, historyRecords) { record ->
            // 點擊某個歷史紀錄進入詳細頁面
            val intent = Intent(this@HistoryActivity, DetailActivity::class.java)
            intent.putExtra("record_id", record.date)  // 傳遞紀錄的日期作為 ID
            startActivity(intent)
        }

        // 將 Adapter 設置到 RecyclerView
        historyRecyclerView.adapter = historyAdapter
    }

    // 加載模擬數據的方法
    private fun loadHistoryRecords(): List<HistoryRecord> {
        return listOf(
            HistoryRecord("2024-01-01", "合格"),
            HistoryRecord("2024-01-02", "不合格"),
            HistoryRecord("2024-01-03", "合格"),
            HistoryRecord("2024-01-04", "不合格")
        )
    }
}

// 定義適配器類別，並設置點擊事件
class HistoryAdapter(
    private val context: Context,
    private val historyRecords: List<HistoryRecord>,
    private val itemClickListener: (HistoryRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // 創建 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    // 綁定數據到 ViewHolder
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = historyRecords[position]
        holder.historyDate.text = record.date
        holder.historyResult.text = record.result

        // 設置點擊事件
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
    val date: String,
    val result: String
)