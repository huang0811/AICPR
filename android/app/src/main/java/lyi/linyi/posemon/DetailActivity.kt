package lyi.linyi.posemon

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // 獲取從 Intent 傳來的紀錄 ID
        val recordId = intent.getStringExtra("record_id")

        // 顯示紀錄 ID
        val detailTextView: TextView = findViewById(R.id.detailTextView)
        detailTextView.text = "詳細紀錄：$recordId"
    }
}