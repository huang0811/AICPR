package lyi.linyi.posemon

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class BattleResultActivity : AppCompatActivity() {

    private lateinit var ibBack: ImageButton
    private lateinit var ibRestart: ImageButton
    private lateinit var ivPass: ImageView
//    private lateinit var tvTotalTime:TextView
    private lateinit var tableLayout: TableLayout
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle_result)


        ivPass=findViewById(R.id.icPass)
        ibBack= findViewById(R.id.ibBack)
        ibRestart=findViewById(R.id.ibRestart)

        tableLayout = findViewById(R.id.tableLayout)

//        updateTabelView()//更新表格



//        tvTotalTime=findViewById(R.id.tvTotalTime)


        ibBack.setOnClickListener {
            // 點擊按鈕時，啟動 MainActivity
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
            // 關閉當前的 MainActivity2
            finish()
        }
        ibRestart.setOnClickListener {
            // 點擊按鈕時，啟動 BattleActivity
            val intent = Intent(this, BattleActivity::class.java)
            startActivity(intent)
            // 關閉當前的 MainActivity2
            finish()
        }

//        ShowTotalTime()

    }

    var passcount=0
    // 函數用於創建 TextView
    private fun updateTabelView() {
        val dataValues = mutableListOf(
            mutableListOf("", "深度(cm)", "按壓頻率", "角度(左手)", "角度(右手)"),
            mutableListOf("分數(%)", "0","0","0","0"),
//            mutableListOf("第二次",  "0","0","0","0"),
//            mutableListOf("第三次", "0","0","0","0"),
//            mutableListOf("第四次", "0","0","0","0"),
//            mutableListOf("第五次", "0","0","0","0"),
            mutableListOf("是否通過",  "不通過","不通過","不通過","不通過")
//            mutableListOf("平均",  "0","0","0","0")
        )
        // 接收列表數據

        val maxDiffDataList = intent.getParcelableArrayListExtra<MaxDiffData>("maxDiffDataList")
        if (maxDiffDataList != null) {
            var totalDeep = 0.0
            var totalFrequency = 0.0
            var totalLeftAngle = 0.0
            var totalRightAngle = 0.0
            for (i in 0 until minOf(maxDiffDataList.size, 5)) {

                val maxDiffData = maxDiffDataList[i]

                val rowIndex = i + 1

                // 更新 dataValues 中的相應行
                dataValues[rowIndex][1] = String.format("%.1f", maxDiffData.deep.toFloat())
                dataValues[rowIndex][2] = String.format("%.1f", maxDiffData.frequency)
                dataValues[rowIndex][3] =String.format("%.1f", maxDiffData.leftAngle)
                dataValues[rowIndex ][4] =String.format("%.1f", maxDiffData.rightAngle)


                // 計算總和
                totalDeep += maxDiffData.deep
                totalFrequency += maxDiffData.frequency
                totalLeftAngle += maxDiffData.leftAngle
                totalRightAngle += maxDiffData.rightAngle
            }
            // 計算平均值
            val rowCount = minOf(maxDiffDataList.size,5 )
            /*  計算是否通過 */
            if(totalDeep/ rowCount>=50) {
                dataValues[2][1] = String.format("通過")
                passcount++
            }
            if(totalFrequency.toFloat() / rowCount>=50) {
                dataValues[2][2] = String.format("通過")
                passcount++
            }
            if(totalLeftAngle.toFloat() / rowCount>=50) {
                dataValues[2][3] = String.format("通過")
                passcount++
            }
            if(totalRightAngle.toFloat()/ rowCount>=50) {
                dataValues[2][4] = String.format("通過")
                passcount++
            }


            if(passcount<3){
                ivPass.setImageResource(R.drawable.ic_fail)
            }


            /*  計算平均 */
//            dataValues[6][1] = String.format("%.1f", totalDeep / rowCount)
//            dataValues[6][2] = String.format("%.1f", totalFrequency.toFloat() / rowCount)
//            dataValues[6][3] = String.format("%.1f", totalLeftAngle.toFloat() / rowCount)
//            dataValues[6][4] = String.format("%.1f", totalRightAngle.toFloat()/ rowCount)
        }



        // 更新表格中的數據
        dataValues.forEachIndexed { rowIndex, rowData ->
            if (rowIndex < tableLayout.childCount) {
                val tableRow = tableLayout.getChildAt(rowIndex) as TableRow
                rowData.forEachIndexed { colIndex, value ->
                    if (colIndex < tableRow.childCount) {
                        val textView = tableRow.getChildAt(colIndex) as TextView
                        textView.text = value.toString()

                        // 根據奇偶性設定背景顏色
                        val backgroundColor = if (rowIndex % 2 == 0) {
//                            Color.parseColor("#D0D0D0") // 偶數列
                            Color.parseColor("#FFC0CB") // 淺粉色
                        } else {
//                            Color.parseColor("#ADADAD") // 奇數列
                            Color.parseColor("#87CEEB") // 淺藍色
                        }
                        textView.setBackgroundColor(backgroundColor)
//                        tableLayout.background = ContextCompat.getDrawable(this, R.drawable.custom_table_background)

                    }
                }
            }
        }
    }

//    private fun ShowTotalTime() {
//        val timeExtra = intent.getIntExtra("EXTRA_TIME", 0)       // 獲取 Intent 中的時間資訊
//
//        val minutes = timeExtra / 60
//        val remainingSeconds = timeExtra % 60
//
//        // 格式化時間顯示，補零
//        val timeString = String.format("總按壓時間:  %02d:%02d", minutes, remainingSeconds)
//        tvTotalTime.text = timeString
//    }
}