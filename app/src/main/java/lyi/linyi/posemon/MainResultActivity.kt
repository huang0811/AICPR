package lyi.linyi.posemon

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.*

class MainResultActivity : AppCompatActivity() {

    private lateinit var btnReport: ImageButton
    private lateinit var btnRestart: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var tableLayout: TableLayout

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_result)

        // 綁定按鈕和表格佈局
        btnReport = findViewById(R.id.btnReport)
        btnRestart = findViewById(R.id.btnRestart)
        btnHistory = findViewById(R.id.btnHistory)
        btnHome = findViewById(R.id.btnHome)
        tableLayout = findViewById(R.id.tableLayout)

        updateTableView() // 更新表格

        // 綁定按鈕事件
        btnReport.setOnClickListener {
            // 這裡添加您的評估報告邏輯
        }

        btnRestart.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnHistory.setOnClickListener {
            // 這裡添加歷史記錄邏輯
        }

        btnHome.setOnClickListener {
            // 返回主菜單
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun updateTableView() {
        val dataValues = mutableListOf(
            mutableListOf("", "深度", "頻率", "角度", "循環"),
            mutableListOf("第一次", "0", "0", "0", "未完成"),
            mutableListOf("第二次", "0", "0", "0", "未完成"),
            mutableListOf("第三次", "0", "0", "0", "未完成"),
            mutableListOf("第四次", "0", "0", "0", "未完成"),
            mutableListOf("第五次", "0", "0", "0", "未完成"),
            mutableListOf("是否合格", "合格", "合格", "合格", "合格")
        )
        val maxDiffDataList = intent.getParcelableArrayListExtra<MaxDiffData>("maxDiffDataList")
        if (maxDiffDataList != null) {
            var totalDeep = 0.0
            var totalFrequency = 0.0
            var totalLeftAngle = 0.0
            var totalRightAngle = 0.0
            val completionStatus = mutableListOf("未完成", "未完成", "未完成", "未完成", "未完成")

            for (i in 0 until minOf(maxDiffDataList.size, 5)) {
                val maxDiffData = maxDiffDataList[i]
                val rowIndex = i + 1

                // 計算平均值
                val averageDepth = maxDiffData.deep.toFloat() // 第一次循環平均按壓深度
                val averageFrequency = maxDiffData.frequency.toFloat() // 第一次循環平均按壓頻率
                val averageAngle = (maxDiffData.leftAngle + maxDiffData.rightAngle) / 2 // 雙手平均按壓角度
                val cycleCompleted = if (maxDiffData.isCycleCompleted) "完成" else "未完成" // 循環是否完成

                // 更新 dataValues 中的相應行
                dataValues[rowIndex][1] = String.format("%.1f", averageDepth)
                dataValues[rowIndex][2] = String.format("%.1f", averageFrequency)
                dataValues[rowIndex][3] = String.format("%.1f", averageAngle)
                dataValues[rowIndex][4] = cycleCompleted

                // 判斷是否合格並更新是否合格行
                if (averageDepth !in 5.0..6.0) {
                    dataValues[6][1] = "不合格"
                }
                if (averageFrequency !in 100.0..120.0) {
                    dataValues[6][2] = "不合格"
                }
                if (averageAngle < 165) {
                    dataValues[6][3] = "不合格"
                }
                if (!maxDiffData.isCycleCompleted) {
                    dataValues[6][4] = "不合格"
                }

                // 計算總和
                totalDeep += maxDiffData.deep
                totalFrequency += maxDiffData.frequency
                totalLeftAngle += maxDiffData.leftAngle
                totalRightAngle += maxDiffData.rightAngle
            }


            // 計算平均值並判斷是否合格
            val rowCount = minOf(maxDiffDataList.size, 5)
            dataValues[6][1] = if (totalDeep / rowCount in 5.0..6.0) "合格" else "不合格"
            dataValues[6][2] = if (totalFrequency / rowCount in 100.0..120.0) "合格" else "不合格"
            dataValues[6][3] =
                if ((totalLeftAngle + totalRightAngle) / (2 * rowCount) >= 165) "合格" else "不合格"
            dataValues[6][4] = if (maxDiffDataList.all { it.isCycleCompleted }) "合格" else "不合格"
        }

        // 更新表格中的數據
        dataValues.forEachIndexed { rowIndex, rowData ->
            if (rowIndex < tableLayout.childCount) {
                val tableRow = tableLayout.getChildAt(rowIndex) as TableRow
                rowData.forEachIndexed { colIndex, value ->
                    if (colIndex < tableRow.childCount) {
                        val textView = tableRow.getChildAt(colIndex) as TextView
                        textView.text = value.toString()

                        // 根據數據判斷是否符合標準，如果不符合，則標紅字
                        if (rowIndex > 0 && rowIndex < 6) { // 針對數據行
                            when (colIndex) {
                                1 -> { // 深度列
                                    val depthValue = value.toFloat()
                                    if (depthValue !in 5.0..6.0) {
                                        textView.setTextColor(Color.RED)
                                    } else {
                                        textView.setTextColor(Color.BLACK)
                                    }
                                }
                                2 -> { // 頻率列
                                    val frequencyValue = value.toFloat()
                                    if (frequencyValue !in 100.0..120.0) {
                                        textView.setTextColor(Color.RED)
                                    } else {
                                        textView.setTextColor(Color.BLACK)
                                    }
                                }
                                3 -> { // 角度列
                                    val angleValue = value.toFloat()
                                    if (angleValue < 165) {
                                        textView.setTextColor(Color.RED)
                                    } else {
                                        textView.setTextColor(Color.BLACK)
                                    }
                                }
                                4 -> { // 循環列
                                    if (value == "未完成") {
                                        textView.setTextColor(Color.RED)
                                    } else {
                                        textView.setTextColor(Color.BLACK)
                                    }
                                }
                            }
                        } else { // 其他行，重置顏色
                            textView.setTextColor(Color.BLACK)
                        }
                    }
                }
            }
        }
    }
}