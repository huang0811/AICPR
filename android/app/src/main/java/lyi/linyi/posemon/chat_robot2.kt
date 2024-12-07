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
    private val MAX_ROUNDS = 7
    private var currentRound = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_robot2)

        Log.d("chat_robot2", "Activity created")

        chatgptAPI = ChatgptAPI(getString(R.string.openai_api_key))

        val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        findViewById<ImageButton>(R.id.ibchatback).setOnClickListener {
            navigateToSelectActivity()
        }

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
        Log.d("chat_robot2", "User choice: $userMessage")
        addMessage(userMessage, isUser = true)

        if (userMessage.contains("練習", ignoreCase = true)) {
            Log.d("chat_robot2", "Navigating to practice page")
            addMessage("進入練習。", isUser = false)
            handler.postDelayed({ navigateToPracticePage() }, 2000)
            return
        }

        if (messages.size > 10) {
            messages.removeAt(0)
        }

        currentRound++
        disableChoiceButtons()
        startWaitingIndicator()

        chatgptAPI.sendMessage(messages, currentRound, userMessage) { response ->
            runOnUiThread {
                stopWaitingIndicator()
                Log.d("chat_robot2", "Response received: $response")
                addMessage(response, isUser = false)

                chatgptAPI.generateChoices(messages, currentRound) { generatedChoices ->
                    runOnUiThread {
                        Log.d("chat_robot2", "Generated choices: $generatedChoices")
                        updateChoicesWithGenerated(generatedChoices)
                        enableChoiceButtons()
                    }
                }
            }
        }
    }

    private fun navigateToPracticePage() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun addMessage(message: String, isUser: Boolean) {
        messages.add(ChatMessage(message, isUser))
        chatAdapter.notifyItemInserted(messages.size - 1)
        findViewById<RecyclerView>(R.id.chatRecyclerView).scrollToPosition(messages.size - 1)
    }

    private fun updateChoicesWithGenerated(choices: List<String>) {
        findViewById<Button>(R.id.choiceButton1).text = choices.getOrNull(0) ?: "開始按壓"
        findViewById<Button>(R.id.choiceButton2).text = choices.getOrNull(1) ?: "學習按壓技巧"
        findViewById<Button>(R.id.choiceButton3).text = choices.getOrNull(2) ?: "進入練習"
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
        }, 20000)
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