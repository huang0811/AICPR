package lyi.linyi.posemon

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore


class DetailHistoryActivity : AppCompatActivity() {
    private lateinit var tableLayout: TableLayout
    private var modeType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 獲取模式類型和紀錄 ID
        modeType = intent.getStringExtra("mode_type")
        val recordId = intent.getStringExtra("record_id")

        // 根據模式類型選擇不同佈局
        if (modeType == "battle") {
            setContentView(R.layout.activity_detail_history_battle)
        } else {
            setContentView(R.layout.activity_detail_history_normal)
        }

        tableLayout = findViewById(R.id.tableLayout)

        // 获取传递的详细数据
        val detailedData = intent.getSerializableExtra("detailed_data") as? List<Map<String, Any>>
        detailedData?.let {
            if (modeType == "battle") {
                displayAverageData(it)
            } else {
                displayDetailedData(it)
            }
        }
    }

    // 显示一般模式的详细数据
    private fun displayDetailedData(detailedData: List<Map<String, Any>>) {
        detailedData.forEach { data ->
            val row = TableRow(this)
            row.addView(createColoredTextView(data["depth"], 5.0f..6.0f))
            row.addView(createColoredTextView(data["frequency"], 100.0f..120.0f))
            row.addView(createColoredTextView(data["angle"], minValue = 165f))
            row.addView(createCompletionTextView(data["cycleCompleted"] as? Boolean ?: false))
            tableLayout.addView(row)
        }
    }

    // 显示对战模式的平均数据
    private fun displayAverageData(detailedData: List<Map<String, Any>>) {
        var totalDepth = 0f
        var totalFrequency = 0f
        var totalAngle = 0f
        var cycleCount = 0

        detailedData.forEach { data ->
            val depth = data["depth"].toString().toFloatOrNull() ?: 0f
            val frequency = data["frequency"].toString().toFloatOrNull() ?: 0f
            val angle = data["angle"].toString().toFloatOrNull() ?: 0f
            val cycleCompleted = data["cycleCompleted"] as? Boolean ?: false

            totalDepth += depth
            totalFrequency += frequency
            totalAngle += angle
            if (cycleCompleted) cycleCount++
        }

        val rowCount = detailedData.size.coerceAtLeast(1)
        val averageDepth = totalDepth / rowCount
        val averageFrequency = totalFrequency / rowCount
        val averageAngle = totalAngle / rowCount
        val averageCycle = cycleCount == rowCount

        val row = TableRow(this)
        row.addView(createColoredTextView(averageDepth, 5.0f..6.0f))
        row.addView(createColoredTextView(averageFrequency, 100.0f..120.0f))
        row.addView(createColoredTextView(averageAngle, minValue = 165f))
        row.addView(createCompletionTextView(averageCycle))
        tableLayout.addView(row)
    }

    private fun createColoredTextView(value: Any?, range: ClosedFloatingPointRange<Float>? = null, minValue: Float? = null): TextView {
        val textView = TextView(this)
        val floatValue = value.toString().toFloatOrNull() ?: 0f
        textView.text = floatValue.toString()
        textView.textSize = 16f

        // 检查值是否在指定范围内或达到最小值要求
        if ((range != null && (floatValue !in range)) || (minValue != null && floatValue < minValue)) {
            textView.setTextColor(Color.parseColor("#e63c3c"))
        }
        return textView
    }

    private fun createCompletionTextView(isComplete: Boolean): TextView {
        val textView = TextView(this)
        textView.text = if (isComplete) "完成" else "未完成"
        textView.textSize = 16f
        textView.setTextColor(if (isComplete) Color.BLACK else Color.parseColor("#e63c3c"))
        return textView
    }
}