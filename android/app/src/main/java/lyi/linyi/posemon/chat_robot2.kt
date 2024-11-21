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
    private var isWaitingForResponse = false
    private val MAX_ROUNDS = 7 // 限制對話輪數
    private var currentRound = 0 // 追蹤當前對話回合

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_robot2)

        Log.d("chat_robot2", "onCreate called in chat_robot2")

        chatgptAPI = ChatgptAPI(getString(R.string.openai_api_key))

        val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        // 返回按鈕的點擊事件
        findViewById<ImageButton>(R.id.ibchatback).setOnClickListener {
            Log.d("chat_robot2", "Back button clicked")
            navigateToSelectActivity()
        }

        findViewById<Button>(R.id.choiceButton1).setOnClickListener {
            Log.d("chat_robot2", "ChoiceButton1 clicked")
            handleUserChoice(findViewById<Button>(R.id.choiceButton1).text.toString())
        }
        findViewById<Button>(R.id.choiceButton2).setOnClickListener {
            Log.d("chat_robot2", "ChoiceButton2 clicked")
            handleUserChoice(findViewById<Button>(R.id.choiceButton2).text.toString())
        }
        findViewById<Button>(R.id.choiceButton3).setOnClickListener {
            Log.d("chat_robot2", "ChoiceButton3 clicked")
            handleUserChoice(findViewById<Button>(R.id.choiceButton3).text.toString())
        }
    }

    private fun handleUserChoice(userMessage: String) {
        Log.d("chat_robot2", "handleUserChoice called with message: $userMessage")
        addMessage(userMessage, isUser = true)

        // 檢查是否包含「練習」字眼，直接跳轉到練習頁面
        if (userMessage.contains("練習", ignoreCase = true)) {
            Log.d("chat_robot2", "User message contains '練習', navigating to practice page")
            addMessage("進入練習。", isUser = false)
            handler.postDelayed({
                Log.d("chat_robot2", "Handler executing navigateToPracticePage")
                navigateToPracticePage()
            }, 2000) // 延遲2秒跳轉
            return
        }

        // 當達到第七次對話，自動跳轉到練習頁面
        if (currentRound >= MAX_ROUNDS) {
            Log.d("chat_robot2", "Reached max rounds, navigating to practice page")
            addMessage("教學結束，開始練習！", isUser = false)
            handler.postDelayed({
                Log.d("chat_robot2", "Handler executing navigateToPracticePage after max rounds")
                navigateToPracticePage()
            }, 2000)
            return
        }

        currentRound++ // 增加對話回合數
        disableChoiceButtons()
        startWaitingIndicator()

        // 傳入 currentRound 參數給 sendMessage 方法
        chatgptAPI.sendMessage(messages, currentRound) { response ->
            runOnUiThread {
                stopWaitingIndicator()
                Log.d("chat_robot2", "Received response from chatgptAPI")
                addMessage(response, isUser = false)

                // 傳入 currentRound 參數給 generateChoices 方法
                chatgptAPI.generateChoices(messages, currentRound) { generatedChoices ->
                    runOnUiThread {
                        Log.d("chat_robot2", "Generated choices: $generatedChoices")
                        if (generatedChoices.size >= 3) {
                            updateChoicesWithGenerated(generatedChoices)
                        } else {
                            updateChoicesWithGenerated(listOf("開始按壓", "學習按壓技巧", "進入練習"))
                        }
                        enableChoiceButtons()
                    }
                }
            }
        }
    }

    private fun navigateToPracticePage() {
        Log.d("chat_robot2", "Navigating to chat_robot3")
        val intent = Intent(this, chat_robot3::class.java)
        startActivity(intent)
        Log.d("chat_robot2", "Intent to start chat_robot3 initiated")
        finish() // 結束當前 Activity，確保不會回退到此頁面
        Log.d("chat_robot2", "Activity chat_robot2 finished")
    }

    private fun addMessage(message: String, isUser: Boolean) {
        Log.d("chat_robot2", "Adding message: $message, isUser: $isUser")
        messages.add(ChatMessage(message, isUser))
        chatAdapter.notifyItemInserted(messages.size - 1)
        findViewById<RecyclerView>(R.id.chatRecyclerView).scrollToPosition(messages.size - 1)
    }

    private fun updateChoicesWithGenerated(choices: List<String>) {
        Log.d("chat_robot2", "Updating choices with: $choices")
        findViewById<Button>(R.id.choiceButton1).text = choices.getOrNull(0) ?: "開始按壓"
        findViewById<Button>(R.id.choiceButton2).text = choices.getOrNull(1) ?: "學習按壓技巧"
        findViewById<Button>(R.id.choiceButton3).text = choices.getOrNull(2) ?: "進入練習"
    }

    private fun disableChoiceButtons() {
        Log.d("chat_robot2", "Disabling choice buttons")
        findViewById<Button>(R.id.choiceButton1).isEnabled = false
        findViewById<Button>(R.id.choiceButton2).isEnabled = false
        findViewById<Button>(R.id.choiceButton3).isEnabled = false
    }

    private fun enableChoiceButtons() {
        Log.d("chat_robot2", "Enabling choice buttons")
        findViewById<Button>(R.id.choiceButton1).isEnabled = true
        findViewById<Button>(R.id.choiceButton2).isEnabled = true
        findViewById<Button>(R.id.choiceButton3).isEnabled = true
    }

    private fun startWaitingIndicator() {
        Log.d("chat_robot2", "Starting waiting indicator")
        isWaitingForResponse = true
        handler.postDelayed({
            if (isWaitingForResponse) {
                addMessage("請稍等...", isUser = false)
            }
        }, 20000)
    }

    private fun stopWaitingIndicator() {
        Log.d("chat_robot2", "Stopping waiting indicator")
        isWaitingForResponse = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun navigateToSelectActivity() {
        Log.d("chat_robot2", "Navigating to SelectActivity")
        val intent = Intent(this, SelectActivity::class.java)
        startActivity(intent)
        finish()
    }
}