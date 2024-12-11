package lyi.linyi.posemon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    // 定義ViewHolder，用來表示單條訊息的視圖
    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userMessage: TextView = view.findViewById(R.id.userMessage)
        val aiMessage: TextView = view.findViewById(R.id.aiMessage)
        val aiAvatar: ImageView = view.findViewById(R.id.aiAvatar)  // 加入AI的头像
    }

    // 創建ViewHolder，並設置每個項目的佈局
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.tem_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    // 綁定數據到ViewHolder
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        // 判斷訊息是來自用戶還是AI
        if (message.isUser) {
            // 用戶的訊息，顯示在右側，隱藏AI的訊息和头像
            holder.userMessage.text = message.message
            holder.userMessage.visibility = View.VISIBLE
            holder.aiMessage.visibility = View.GONE
            holder.aiAvatar.visibility = View.GONE  // 隱藏AI头像
        } else {
            // AI的訊息，顯示在左側，隱藏用戶的訊息
            holder.aiMessage.text = message.message
            holder.aiMessage.visibility = View.VISIBLE
            holder.userMessage.visibility = View.GONE
            holder.aiAvatar.visibility = View.VISIBLE  // 顯示AI头像
        }
    }
    // 返回訊息列表的數量
    override fun getItemCount() = messages.size
}
