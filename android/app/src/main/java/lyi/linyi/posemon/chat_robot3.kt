package lyi.linyi.posemon

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class chat_robot3 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_robot3) // 設定對應的 XML 佈局檔案

        // 假設測試用的數據，顯示在列表中
        val data = listOf("⨀訓練環境設置:", "主鏡頭離地35公分且同時距離安妮左奶頭85公分，保持背景淨空，光線明亮",
            "⨀按壓深度:", "按壓深度應為 5-6 公分，避免過淺或過深。如果按壓深度過淺，APP會語音提示[請增加按壓深度]",
            "⨀按壓頻率:", "按壓頻率應為 100~120次/分鐘，避免過快或過慢。如果按壓速度過快，APP會語音提示[請放慢速度]，反之，若太慢就會有[請加快速度]",
            "⨀按壓姿勢:", "雙手打直，手掌交疊，雙手手肘角度應大於165度。如果按壓姿勢錯誤，APP會語音提示[按壓姿勢異常]",
            "⨀按壓循環次數:", "訓練總共提供五個循環，每循環30下，五循環完成即進入結果畫面。",
            "⨀按壓頻率的檢測:", "可以跟著APP中的背景樂(節拍器)，輔助你的按壓速度")

        // 獲取佈局檔案中的 RecyclerView
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)

        // 設定 RecyclerView 的布局管理器 (垂直線性排列)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 建立自定義適配器並傳入數據
        val adapter = teach_adapter(data) // 傳入數據
        recyclerView.adapter = adapter  // 綁定適配器

        // 將適配器綁定到 RecyclerView
        recyclerView.adapter = adapter

        val ibteachback = findViewById<ImageButton>(R.id.ibteachback)
        ibteachback.setOnClickListener {
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 獲取 nextstep 按鈕
        val nextStepButton: ImageButton = findViewById(R.id.nextstep)
        nextStepButton.setOnClickListener {
            // 使用 Intent 跳轉到 SelectActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}

// 自定義 RecyclerView 適配器類
class teach_adapter(private val data: List<String>) :
    RecyclerView.Adapter<teach_adapter.ViewHolder>() {

    // 創建 ViewHolder 並指定每個項目的佈局檔案
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 加載列表項目佈局 (使用內建的簡單列表項目佈局)
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    // 綁定數據到 ViewHolder
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(data[position]) // 將當前數據項目綁定到 TextView
    }

    // 返回列表項目數量
    override fun getItemCount(): Int {
        return data.size
    }

    // 自定義 ViewHolder，用於處理每個列表項目的視圖
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 定義 TextView，綁定到內建的 android.R.id.text1
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        // 將數據綁定到 TextView
        fun bind(text: String) {
            textView.text = text
        }
    }
}