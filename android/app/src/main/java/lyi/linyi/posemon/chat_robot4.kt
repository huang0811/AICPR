package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class chat_robot4 : AppCompatActivity() {

    private lateinit var chatgptAPI: ChatgptAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_robot4)

        Log.d("chat_robot4", "onCreate called in chat_robot4")

        chatgptAPI = ChatgptAPI(getString(R.string.openai_api_key))

        // 設置返回按鈕的點擊事件
        findViewById<ImageButton>(R.id.ibchatback).setOnClickListener {
            Log.d("chat_robot4", "Back button clicked")
            navigateToSelectActivity()
        }
    }

    private fun navigateToSelectActivity() {
        Log.d("chat_robot4", "Navigating to SelectActivity")
        val intent = Intent(this, SelectActivity::class.java)
        startActivity(intent)
        finish()
    }
}
