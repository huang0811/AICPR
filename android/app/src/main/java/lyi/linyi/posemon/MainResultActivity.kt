package lyi.linyi.posemon

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainResultActivity : AppCompatActivity() {

    private lateinit var btnReport: ImageButton
    private lateinit var btnRestart: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var tableLayout: TableLayout
    private lateinit var dataValues: MutableList<MutableList<String>>

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_result)

        // 從 SharedPreferences 中獲取 userID 和 userName
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        val userID = sharedPref.getString("userID", "Unknown") ?: "Unknown"
        val userName = sharedPref.getString("userName", "Unknown") ?: "Unknown"
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

        // 綁定按鈕和表格佈局
        btnReport = findViewById(R.id.btnReport)
        btnRestart = findViewById(R.id.btnRestart)
        btnHistory = findViewById(R.id.btnHistory)
        btnHome = findViewById(R.id.btnHome)
        tableLayout = findViewById(R.id.tableLayout)

        updateTableView() // 更新表格

        btnReport.setOnClickListener {
            val depthStatus = dataValues[6][1]
            val frequencyStatus = dataValues[6][2]
            val angleStatus = dataValues[6][3]
            val cycleStatus = dataValues[6][4]
            val result = if (listOf(depthStatus, frequencyStatus, angleStatus, cycleStatus).contains("不合格")) "不合格" else "合格"

            // 從 SharedPreferences 中取得 userName，確保優先使用用戶自定義的名稱
            val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
            val userName = sharedPref.getString("userName", userName) ?: "Unknown"

            val file = generateTestReportPDF(userID, userName, depthStatus, frequencyStatus, angleStatus, cycleStatus, result)
            openPDF(file)
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
        dataValues = mutableListOf(
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
            var totalBothAngle = 0.0
            val completionStatus = mutableListOf("未完成", "未完成", "未完成", "未完成", "未完成")

            for (i in 0 until minOf(maxDiffDataList.size, 5)) {
                val maxDiffData = maxDiffDataList[i]
                val rowIndex = i + 1

                // 計算平均值
                val averageDepth = maxDiffData.deep.toFloat() // 第一次循環平均按壓深度
                val averageFrequency = maxDiffData.frequency.toFloat() // 第一次循環平均按壓頻率
//                val averageAngle = (maxDiffData.leftAngle + maxDiffData.rightAngle) / 2 // 雙手平均按壓角度
                val bothAngle = maxDiffData.bothAngle
                val cycleCompleted =
                    if (maxDiffData.isCycleCompleted) "完成" else "未完成" // 循環是否完成

                // 更新 dataValues 中的相應行
                dataValues[rowIndex][1] = String.format("%.1f", averageDepth)
                dataValues[rowIndex][2] = String.format("%.1f", averageFrequency)
                dataValues[rowIndex][3] = String.format("%.1f", bothAngle)
                dataValues[rowIndex][4] = cycleCompleted

                // 判斷是否合格並更新是否合格行
                if (averageDepth !in 5.0..6.0) {
                    dataValues[6][1] = "不合格"
                }
                if (averageFrequency !in 100.0..120.0) {
                    dataValues[6][2] = "不合格"
                }
                if (bothAngle < 165) {
                    dataValues[6][3] = "不合格"
                }
                if (maxDiffDataList.size < 5) {
                    dataValues[6][4] = "不合格"
                }
//                if (!maxDiffData.isCycleCompleted) {
//                    dataValues[6][4] = "不合格"
//                }

                // 計算總和
                totalDeep += maxDiffData.deep
                totalFrequency += maxDiffData.frequency
                totalBothAngle += maxDiffData.bothAngle
//                totalLeftAngle += maxDiffData.leftAngle
//                totalRightAngle += maxDiffData.rightAngle
            }


            // 計算平均值並判斷是否合格
            val rowCount = minOf(maxDiffDataList.size, 5)
            dataValues[6][1] = if (totalDeep / rowCount in 5.0..6.0) "合格" else "不合格"
            dataValues[6][2] = if (totalFrequency / rowCount in 100.0..120.0) "合格" else "不合格"
            dataValues[6][3] =
                if ((totalBothAngle) / (rowCount) >= 165) "合格" else "不合格"
//            dataValues[6][4] = if (maxDiffDataList.all { it.isCycleCompleted }) "合格" else "不合格"
            if (maxDiffDataList.size < 5) {
                dataValues[6][4] = "不合格"
            }

        }

        // 更新表格中的數據
        dataValues.forEachIndexed { rowIndex, rowData ->
            if (rowIndex < tableLayout.childCount) {
                val tableRow = tableLayout.getChildAt(rowIndex) as TableRow
                rowData.forEachIndexed { colIndex, value ->
                    if (colIndex < tableRow.childCount) {
                        val view = tableRow.getChildAt(colIndex)

                        // 根據視圖類型進行處理
                        if (view is TextView) {
                            view.text = value.toString()

                            // 根據數據判斷是否符合標準，如果不符合，則標紅字
                            if (rowIndex > 0 && rowIndex < 6) { // 針對數據行
                                when (colIndex) {
                                    1 -> { // 深度列
                                        val depthValue = value.toFloatOrNull()
                                        if (depthValue != null && depthValue !in 5.0..6.0) {
                                            view.setTextColor(Color.RED)
                                        } else {
                                            view.setTextColor(Color.BLACK)
                                        }
                                    }

                                    2 -> { // 頻率列
                                        val frequencyValue = value.toFloatOrNull()
                                        if (frequencyValue != null && frequencyValue !in 100.0..120.0) {
                                            view.setTextColor(Color.RED)
                                        } else {
                                            view.setTextColor(Color.BLACK)
                                        }
                                    }

                                    3 -> { // 角度列
                                        val angleValue = value.toFloatOrNull()
                                        if (angleValue != null && angleValue < 165) {
                                            view.setTextColor(Color.RED)
                                        } else {
                                            view.setTextColor(Color.BLACK)
                                        }
                                    }

                                    4 -> { // 循環列
                                        if (value == "未完成") {
                                            view.setTextColor(Color.RED)
                                        } else {
                                            view.setTextColor(Color.BLACK)
                                        }
                                    }
                                }
                            } else { // 其他行，重置顏色
                                view.setTextColor(Color.BLACK)
                            }
                        } else if (view is ImageView) {
                            // 處理 ImageView 的邏輯
                            // 比如設定不同的圖標或圖片
                            // view.setImageResource(R.drawable.some_image)
                        }
                    }
                }
            }
        }
    }

    private fun generateTestReportPDF(
        userID: String,
        userName: String,
        depthStatus: String,
        frequencyStatus: String,
        angleStatus: String,
        cycleStatus: String,
        result: String
    ): File {
        // 使用 getExternalFilesDir() 而不是直接訪問公共存儲目錄
        val pdfTemplateStream: InputStream = assets.open("aicpr_report.pdf")  // 從 assets 資料夾讀取
        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir != null && !documentsDir.exists()) {
            documentsDir.mkdirs() // 確保目錄存在
        }
        val filePath = "$documentsDir/aicpr_report_filled.pdf"
        val outputFile = File(filePath)

        val pdfReader = PdfReader(pdfTemplateStream)
        val pdfWriter = PdfWriter(FileOutputStream(outputFile))
        val pdfDocument = PdfDocument(pdfReader, pdfWriter)
        val document = Document(pdfDocument)

        // 載入字型
        val fontStream = assets.open("NotoSansTC-Medium.ttf") // 加載 assets 文件夾中的字體
        val font = PdfFontFactory.createFont(fontStream.readBytes(), PdfEncodings.IDENTITY_H, true)
        fontStream.close() // 關閉文件流

        // 設置自定義顏色
        val customRed = com.itextpdf.kernel.colors.DeviceRgb(210, 74, 53)

        // 通用函數：插入文字到指定位置，並根據內容設置顏色和對齊方式
        fun addText(
            content: String,
            x: Float,
            y: Float,
            fontSize: Float = 15f,
            alignment: com.itextpdf.layout.property.TextAlignment = com.itextpdf.layout.property.TextAlignment.CENTER
        ) {
            val color = if (content == "不合格") customRed else ColorConstants.BLACK
            document.add(
                Paragraph(content)
                    .setFont(font)
                    .setFontSize(fontSize)
                    .setFontColor(color)
                    .setFixedPosition(x, y, 400f)
                    .setTextAlignment(alignment)
            )
        }

        // 插入使用者資料，左對齊
        addText(userID, 190f, 542f, alignment = com.itextpdf.layout.property.TextAlignment.LEFT)
        addText(userName, 190f, 509f, alignment = com.itextpdf.layout.property.TextAlignment.LEFT)
        val currentDateTime = SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.getDefault()).format(Date())
        addText(currentDateTime, 175f, 471f, alignment = com.itextpdf.layout.property.TextAlignment.LEFT)

        // 插入測驗結果，左對齊
        addText(result, 175f, 393f, fontSize = 40f, alignment = com.itextpdf.layout.property.TextAlignment.LEFT)

        // 插入評估結果，置中對齊
        addText(depthStatus, 285f, 281f, alignment = com.itextpdf.layout.property.TextAlignment.CENTER)
        addText(frequencyStatus, 285f, 231f, alignment = com.itextpdf.layout.property.TextAlignment.CENTER)
        addText(angleStatus, 285f, 181f, alignment = com.itextpdf.layout.property.TextAlignment.CENTER)
        addText(cycleStatus, 285f, 131f, alignment = com.itextpdf.layout.property.TextAlignment.CENTER)

        // 關閉文檔
        document.close()
        pdfDocument.close()
        return outputFile
    }

    private fun openPDF(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (e is ActivityNotFoundException) {
                Toast.makeText(this, "沒有找到可以打開 PDF 的應用程序，請安裝 PDF 閱讀器", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "無法打開 PDF 文件", Toast.LENGTH_LONG).show()
            }
        }
    }
}