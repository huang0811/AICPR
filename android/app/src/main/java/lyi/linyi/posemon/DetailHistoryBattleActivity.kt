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

        // 取得傳遞過來的記錄 ID
        val recordId = intent.getStringExtra("record_id") ?: "Unknown"
        fetchRecordDetails(recordId)
    }

    private fun fetchRecordDetails(recordId: String) {
        firestore.collection("user_history").document(recordId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val result = document.getString("qualificationResult") ?: "Unknown"
                    val date = document.getString("date") ?: "Unknown"
                    val time = document.getString("time") ?: "Unknown"
                    val avgDepth = document.getDouble("avgDepth") ?: 0.0
                    val avgFrequency = document.getDouble("avgFrequency") ?: 0.0
                    val avgAngle = document.getDouble("avgAngle") ?: 0.0
                    val avgCycle = document.getString("avgCycle") ?: "Unknown"

                    // 設定日期和時間
                    dateTextView.text = date
                    timeTextView.text = time

                    // 顯示平均數據
                    avgDepthTextView.text = String.format("%.1f", avgDepth)
                    avgFrequencyTextView.text = String.format("%.1f", avgFrequency)
                    avgAngleTextView.text = String.format("%.1f", avgAngle)
                    avgCycleTextView.text = avgCycle

                    // 顯示結果圖片
                    if (result == "合格") {
                        trophyImageView.visibility = View.VISIBLE
                        grimReaperImageView.visibility = View.GONE
                    } else {
                        trophyImageView.visibility = View.GONE
                        grimReaperImageView.visibility = View.VISIBLE
                    }
                }
            }
            .addOnFailureListener {
                // 取得數據失敗時的處理
                trophyImageView.visibility = View.GONE
                grimReaperImageView.visibility = View.GONE
            }
    }
}