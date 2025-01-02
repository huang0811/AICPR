package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class chat_robot2 : AppCompatActivity() {
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatgptAPI: ChatgptAPI
    private val handler = Handler(Looper.getMainLooper())
    private var conversationCounter = 0
    private val maxConversationCount = 8
    private var isWaitingForResponse = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_robot2)

        // 初始化 ChatgptAPI，使用 API Key
        chatgptAPI = ChatgptAPI(getString(R.string.dify_api_key))

        // 初始化 RecyclerView
        val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        // 返回按鈕
        findViewById<ImageButton>(R.id.ibchatback).setOnClickListener {
            // 使用Intent導航到ChatRobotActivity
            val intent = Intent(this, chat_robot::class.java)
            startActivity(intent)
            // 如果不需要保留當前Activity，可以調用finish()
            finish()
        }

        // 設置選項按鈕
        findViewById<Button>(R.id.choiceButton1).setOnClickListener {
            handleUserChoice(findViewById<Button>(R.id.choiceButton1).text.toString())
        }
        findViewById<Button>(R.id.choiceButton2).setOnClickListener {
            handleUserChoice(findViewById<Button>(R.id.choiceButton2).text.toString())
        }
        findViewById<Button>(R.id.choiceButton3).setOnClickListener {
            handleUserChoice(findViewById<Button>(R.id.choiceButton3).text.toString())
        }
    }

    private fun handleUserChoice(userMessage: String) {
        if (conversationCounter >= maxConversationCount) {
            addMessage("出發前往練習!!", isUser = false)
            disableChoiceButtons()

            // 延遲2秒後跳轉到 chat_robot3
            handler.postDelayed({
                val intent = Intent(this, chat_robot3::class.java)
                startActivity(intent)
                finish() // 結束當前Activity
            }, 2000) // 延遲2秒 (2000 毫秒)
            return
        }
        Log.d("chat_robot2", "User choice: $userMessage")
        addMessage(userMessage, isUser = true)
        conversationCounter++ // 增加對話計數

        // 禁用按鈕，顯示等待指示器
        disableChoiceButtons()
        startWaitingIndicator()

        // 調用 API
        chatgptAPI.sendMessage(userMessage) { response ->
            runOnUiThread {
                stopWaitingIndicator()
                Log.d("chat_robot2", "Response received: $response")
                addMessage(response, isUser = false)

                if (conversationCounter < maxConversationCount) {
                    // 更新選項按鈕
                    val options = getOptionsForNextQuestion(conversationCounter) // 根據對話階段取得選項
                    updateChoiceButtons(options)

                    // 啟用按鈕
                    enableChoiceButtons()
                } else {
                    addMessage("出發前往練習!!", isUser = false)

                    // 延遲2秒後跳轉到 chat_robot3
                    handler.postDelayed({
                        val intent = Intent(this, chat_robot3::class.java)
                        startActivity(intent)
                        finish() // 結束當前Activity
                    }, 2000) // 延遲2秒 (2000 毫秒)
                }
            }
        }
    }

    private fun getOptionsForNextQuestion(stage: Int): List<String> {
        return when (stage) {
            1 -> listOf(
                "如何正確設置訓練環境以獲得最佳效果？",
                "訓練環境需要怎樣的設置來保證正確的訓練？",
                "訓練時，鏡頭和背景的最佳設置是什麼？",
                "練習時，鏡頭和背景的該如何設置？",
                "鏡頭和背景有什麼設置的規定嗎？",
                "環境設置有甚麼特殊需求嗎?"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            2 -> listOf(
                "按壓時，深度應該保持在什麼範圍內？",
                "每次按壓需要達到的深度標準是什麼？",
                "CPR 按壓時的深度需要控制在多少？",
                "按壓的深度是否有具體要求？",
                "訓練時深度的最佳範圍是多少？",
                "按壓深度的有什麼要注意？"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            3 -> listOf(
                "按壓的速度應該保持多少次每分鐘？",
                "CPR 的標準按壓頻率是多少？",
                "按壓時應遵循什麼樣的頻率以符合要求？",
                "按壓頻率應該如何控制？",
                "按壓時的速率有什麼標準嗎？",
                "按壓頻率的調整需要注意什麼？"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            4 -> listOf(
                "正確的按壓姿勢是什麼樣子的？",
                "在 CPR 中，按壓時需要注意的手部姿勢是什麼？",
                "按壓過程中，手肘的角度和姿勢有什麼要求？",
                "按壓姿勢有甚麼規定嗎？",
                "CPR 中，需要注意的手部姿勢嗎？",
                "手肘的角度和姿勢有哪些要求呀？"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            5 -> listOf(
                "CPR 訓練中，標準的按壓循環次數是多少？",
                "每次訓練應完成多少個按壓循環？",
                "按壓循環的次數設置是怎樣的？",
                "CPR 訓練時，應完成的標準按壓循環數是多少？",
                "每次進行 CPR 訓練時，應進行幾個按壓循環？",
                "CPR 訓練中的按壓循環數應如何設定？"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            6 -> listOf(
                "如何確保按壓頻率保持在標準範圍內？",
                "怎樣檢測按壓的速度是否符合要求？",
                "按壓時如何避免過快或過慢？",
                "怎樣確認按壓頻率處於標準範圍內？",
                "如何檢驗按壓速度是否達到規範？",
                "按壓時要如何確保速度不超過或低於標準？"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            7 -> listOf(
                "那一起來練習看好了",
                "快點開始吧",
                "來練習看看好了",
                "讓我們馬上開始",
                "我準備好了!!開始練習吧",
                "趕快來試試吧"
            ).shuffled().take(3) // 隨機打亂順序並取前三個選項
            else -> listOf("選項已結束", "謝謝參與", "再見！")
        }
    }

    private fun updateChoiceButtons(options: List<String>) {
        findViewById<Button>(R.id.choiceButton1).text = options[0]
        findViewById<Button>(R.id.choiceButton2).text = options[1]
        findViewById<Button>(R.id.choiceButton3).text = options[2]
    }

    private fun addMessage(message: String, isUser: Boolean) {
        messages.add(ChatMessage(message, isUser))
        chatAdapter.notifyItemInserted(messages.size - 1)
        findViewById<RecyclerView>(R.id.chatRecyclerView).scrollToPosition(messages.size - 1)
    }

    private fun disableChoiceButtons() {
        findViewById<Button>(R.id.choiceButton1).isEnabled = false
        findViewById<Button>(R.id.choiceButton2).isEnabled = false
        findViewById<Button>(R.id.choiceButton3).isEnabled = false
    }

    private fun enableChoiceButtons() {
        findViewById<Button>(R.id.choiceButton1).isEnabled = true
        findViewById<Button>(R.id.choiceButton2).isEnabled = true
        findViewById<Button>(R.id.choiceButton3).isEnabled = true
    }

    private fun startWaitingIndicator() {
        isWaitingForResponse = true
        handler.postDelayed({
            if (isWaitingForResponse) {
                addMessage("請稍等...", isUser = false)
            }
        }, 6000)
    }

    private fun stopWaitingIndicator() {
        isWaitingForResponse = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun navigateToSelectActivity() {
        val intent = Intent(this, SelectActivity::class.java)
        startActivity(intent)
        finish()
    }
}