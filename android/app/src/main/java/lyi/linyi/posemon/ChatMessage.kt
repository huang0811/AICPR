package lyi.linyi.posemon

// 定義一個數據類來表示聊天訊息
data class ChatMessage(
    val message: String,   // 訊息的內容
    val isUser: Boolean    // 判斷這條訊息是用戶的還是AI的
)
