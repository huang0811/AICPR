package lyi.linyi.posemon

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class DetailHistoryBattleActivity : AppCompatActivity() {

    private lateinit var trophyImageView: ImageView
    private lateinit var grimReaperImageView: ImageView
    private lateinit var dateTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var avgDepthTextView: TextView
    private lateinit var avgFrequencyTextView: TextView
    private lateinit var avgAngleTextView: TextView
    private lateinit var avgCycleTextView: TextView
    private lateinit var battleModeTableLayout: TableLayout
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_history_battle)

        // 綁定元件
        trophyImageView = findViewById(R.id.trophyImageView)
        grimReaperImageView = findViewById(R.id.grimReaperImageView)
        dateTextView = findViewById(R.id.dateTextView)
        timeTextView = findViewById(R.id.timeTextView)
        avgDepthTextView = findViewById(R.id.avgDepthTextView)
        avgFrequencyTextView = findViewById(R.id.avgFrequencyTextView)
        avgAngleTextView = findViewById(R.id.avgAngleTextView)
        avgCycleTextView = findViewById(R.id.avgCycleTextView)
        battleModeTableLayout = findViewById(R.id.battleModeTableLayout)
        firestore = FirebaseFirestore.getInstance()

        // 返回按鈕
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // 取得传递的 document_id
        val documentId = intent.getStringExtra("document_id") ?: "Unknown"

        if (documentId == "Unknown") {
            showErrorState()
            return
        }

        // 从 Firestore 获取详细数据
        fetchRecordDetails(documentId)
    }

    private fun fetchRecordDetails(documentId: String) {
        firestore.collection("user_history").document(documentId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val result = document.getString("qualificationResult") ?: "Unknown"
                     val timestamp = document.getString("timestamp") ?: "Unknown"
                    val avgDepth = document.getDouble("averageDepth") ?: 0.0
                    val avgFrequency = document.getDouble("averageFrequency") ?: 0.0
                    val avgAngle = document.getDouble("averageAngle") ?: 0.0
                    val cycles = document.getLong("cycles") ?: 0L

                    // 分割 timestamp 為日期和時間
                    val date = timestamp.substringBefore(" ") // 取空格前的日期部分
                    val time = timestamp.substringAfter(" ")  // 取空格後的時間部分

                    // 設定日期和時間
                    dateTextView.text = getString(R.string.date_label, date)
                    timeTextView.text = getString(R.string.time_label, time)

                    // 顯示平均數據
                    updateTextView(avgDepthTextView, avgDepth, 5.0..6.0)
                    updateTextView(avgFrequencyTextView, avgFrequency, 100.0..120.0)
                    updateTextView(avgAngleTextView, avgAngle, 165.0..180.0)
                    avgCycleTextView.text = cycles.toString()


                    // 顯示結果圖片
                        trophyImageView.visibility = if (result == "勝利") View.VISIBLE else View.GONE
                        grimReaperImageView.visibility = if (result != "勝利") View.VISIBLE else View.GONE
                } else {
                    showErrorState()
                }
            }
            .addOnFailureListener {
                showErrorState()
            }
    }

    private fun showErrorState() {
        trophyImageView.visibility = View.GONE
        grimReaperImageView.visibility = View.GONE
        dateTextView.text = ""
        timeTextView.text = ""
        avgDepthTextView.text = "-"
        avgFrequencyTextView.text = "-"
        avgAngleTextView.text = "-"
        avgCycleTextView.text = "-"
    }

    private fun updateTextView(textView: TextView, value: Double, range: ClosedFloatingPointRange<Double>) {
        textView.text = String.format("%.1f", value)
        if (value in range) {
            textView.setTextColor(getColor(R.color.black)) // 正常顯示為黑色
        } else {
            textView.setTextColor(getColor(R.color.red)) // 超出範圍顯示為紅色
        }
    }
}