package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp

// 定义主 Activity 类别
class chat_robot : AppCompatActivity() {
    // 定义对话内容的列表
    private val dialogues = listOf("你好，歡迎來到CPR王國!我是 CPR MAN", "身為專業急救英雄，我的任務是在危急時刻，利用心肺復甦術（CPR）拯救生命。", "我待會會一步一步教導您!那就讓我們開始吧!!") // 替换成你的实际对话内容
    // 记录当前显示的对话索引，初始值为 0
    private var currentDialogueIndex = 0

    // 覆写 onCreate 方法， 这是在 Activity 创建时执行的代码
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Firebase，确保在使用 Firebase 之前执行此操作
        FirebaseApp.initializeApp(this)

        // 设置 Activity 的布局文件
        setContentView(R.layout.activity_chat_robot)

        // 获取布局中的 TextView，用于显示对话内容
        val dialogueTextView = findViewById<TextView>(R.id.tv_response)
        // 获取布局中的“Next”按钮
        val nextButton = findViewById<ImageButton>(R.id.ibnext)
        // 获取布局中的“Back”按钮
        val backButton = findViewById<ImageButton>(R.id.ibBack)

        // 设置“Next”按钮的点击事件监听器
        nextButton.setOnClickListener {
            // 检查当前对话索引是否小于对话列表的大小，确保还有对话可以显示
            if (currentDialogueIndex < dialogues.size) {
                // 定义每次显示的最大字符数量
                val maxChars = 50
                // 获取当前索引对应的对话内容
                val text = dialogues[currentDialogueIndex]
                // 根据对话内容的长度决定显示的文字，超过 maxChars 时显示省略号
                dialogueTextView.text = if (text.length > maxChars) {
                    text.substring(0, maxChars) + "..." // 只显示前 maxChars 个字符，并加上省略号
                } else {
                    text // 如果长度在范围内，直接显示整段文字
                }
                // 将对话索引加 1，以便下一次点击显示下一句对话
                currentDialogueIndex++
            } else {
                // 如果所有对话都显示完毕，进入第二步骤
                val intent = Intent(this, chat_robot2::class.java) // 确保使用正确的类名
                startActivity(intent)
            }
        }

        // 设置“Back”按钮的点击事件监听器
        backButton.setOnClickListener {
            // 如果当前对话索引大于 0，回到上一句对话
            if (currentDialogueIndex > 0) {
                currentDialogueIndex-- // 将对话索引减 1
                // 更新显示的对话内容
                val maxChars = 50
                val text = dialogues[currentDialogueIndex]
                dialogueTextView.text = if (text.length > maxChars) {
                    text.substring(0, maxChars) + "..."
                } else {
                    text
                }
            } else {
                // 如果当前是第一句对话，返回到 SelectActivity
                val intent = Intent(this, SelectActivity::class.java)
                startActivity(intent)
            }
        }
    }
}