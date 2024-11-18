package lyi.linyi.posemon

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class DetailHistoryNormalActivity : AppCompatActivity() {

    private lateinit var btnReport: ImageButton
    private lateinit var firestore: FirebaseFirestore
    private lateinit var document_id: String
    private lateinit var detailedData: List<Map<String, Any>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_history_normal)

        firestore = FirebaseFirestore.getInstance()

        // 取得傳遞過來的數據
        document_id = intent.getStringExtra("document_id") ?: "Unknown"
        detailedData = intent.getSerializableExtra("detailed_data") as? List<Map<String, Any>> ?: emptyList()
        val timestamp = intent.getStringExtra("timestamp") ?: "Unknown" // 获取传递的 timestamp

        if (document_id == "Unknown" || detailedData.isEmpty()) {
            Toast.makeText(this, "数据加载失败", Toast.LENGTH_SHORT).show()
            finish() // 返回到上一个界面
            return
        }
        // 绑定 dateTextView 并显示 timestamp
        val dateTextView = findViewById<TextView>(R.id.dateTextView)
        dateTextView.text = "DATE:" + timestamp // 显示日期

        // 綁定元件
        btnReport = findViewById(R.id.btnReport)

        // 填充表格數據
        fillTableData()

        // PDF 輸出按鈕
        btnReport.setOnClickListener {
            fetchPdfUrlAndOpen()
        }

        // 返回按鈕
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun fillTableData() {
        // 定義表格中的各個欄位
        val depthViews = listOf(
            findViewById<TextView>(R.id.tvFirstDepth),
            findViewById<TextView>(R.id.tvSecondDepth),
            findViewById<TextView>(R.id.tvThirdDepth),
            findViewById<TextView>(R.id.tvFourthDepth),
            findViewById<TextView>(R.id.tvFifthDepth)
        )

        val frequencyViews = listOf(
            findViewById<TextView>(R.id.tvFirstFrequency),
            findViewById<TextView>(R.id.tvSecondFrequency),
            findViewById<TextView>(R.id.tvThirdFrequency),
            findViewById<TextView>(R.id.tvFourthFrequency),
            findViewById<TextView>(R.id.tvFifthFrequency)
        )

        val angleViews = listOf(
            findViewById<TextView>(R.id.tvFirstAngle),
            findViewById<TextView>(R.id.tvSecondAngle),
            findViewById<TextView>(R.id.tvThirdAngle),
            findViewById<TextView>(R.id.tvFourthAngle),
            findViewById<TextView>(R.id.tvFifthAngle)
        )

        val cycleViews = listOf(
            findViewById<TextView>(R.id.tvFirstCycle),
            findViewById<TextView>(R.id.tvSecondCycle),
            findViewById<TextView>(R.id.tvThirdCycle),
            findViewById<TextView>(R.id.tvFourthCycle),
            findViewById<TextView>(R.id.tvFifthCycle)
        )

        // 填充數據到表格
        for (i in detailedData.indices) {
            val data = detailedData[i]
            depthViews[i].text = data["depth"].toString()
            frequencyViews[i].text = data["frequency"].toString()
            angleViews[i].text = data["angle"].toString()
            cycleViews[i].text = if (data["cycleCompleted"] as Boolean) "完成" else "未完成"
        }

        // 填充數據到表格
        for (i in detailedData.indices) {
            val data = detailedData[i]
            depthViews[i].text = data["depth"].toString()
            frequencyViews[i].text = data["frequency"].toString()
            angleViews[i].text = data["angle"].toString()
            cycleViews[i].text = if (data["cycleCompleted"] as Boolean) "完成" else "未完成"

            // 判斷是否合格並設置紅字
            if ((data["depth"] as? Double ?: 0.0) !in 5.0..6.0) {
                depthViews[i].setTextColor(Color.RED)
            } else {
                depthViews[i].setTextColor(Color.BLACK)
            }

            if ((data["frequency"] as? Double ?: 0.0) !in 100.0..120.0) {
                frequencyViews[i].setTextColor(Color.RED)
            } else {
                frequencyViews[i].setTextColor(Color.BLACK)
            }

            if ((data["angle"] as? Double ?: 0.0) < 165.0) {
                angleViews[i].setTextColor(Color.RED)
            } else {
                angleViews[i].setTextColor(Color.BLACK)
            }

            if (cycleViews[i].text == "未完成") {
                cycleViews[i].setTextColor(Color.RED)
            } else {
                cycleViews[i].setTextColor(Color.BLACK)
            }
        }

        // 設定合格與否的顏色
        setQualificationColor(
            findViewById(R.id.tvIsPassDepth),
            findViewById(R.id.tvIsPassFrequency),
            findViewById(R.id.tvIsPassAngle),
            findViewById(R.id.tvIsPassCycle)
        )
    }

    private fun fetchPdfUrlAndOpen() {
        // 从 Firestore 获取 PDF 的 URL
        firestore.collection("user_history")
            .document(document_id)
            .get()
            .addOnSuccessListener { document ->
                val pdfUrl = document.getString("pdfUrl")
                if (pdfUrl != null) {
                    openPDF(pdfUrl)
                } else {
                    Toast.makeText(this, "未找到 PDF 文件", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "加載 PDF 失敗", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openPDF(pdfUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(pdfUrl), "application/pdf")
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "请安装 PDF 查看器", Toast.LENGTH_LONG).show()
        }
    }

    private fun setQualificationColor(depthView: TextView, frequencyView: TextView, angleView: TextView, cycleView: TextView) {
        // 判斷數據是否合格，並設置顏色和文本
        setTextViewColorAndText(depthView, isQualified(detailedData, "depth", 5.0..6.0))
        setTextViewColorAndText(frequencyView, isQualified(detailedData, "frequency", 100.0..120.0))
        setTextViewColorAndText(angleView, isQualified(detailedData, "angle", 165.0..180.0))
        setTextViewColorAndText(cycleView, detailedData.all { it["cycleCompleted"] == true })
    }

    // 設定顏色和文本的辅助方法
    private fun setTextViewColorAndText(view: TextView, isQualified: Boolean) {
        view.text = if (isQualified) "合格" else "不合格"
        view.setTextColor(if (isQualified) Color.BLACK else Color.RED)
    }

    private fun isQualified(data: List<Map<String, Any>>, key: String, range: ClosedRange<Double>): Boolean {
        return data.all { (it[key] as? Double ?: 0.0) in range }
    }
}
