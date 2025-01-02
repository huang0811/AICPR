package lyi.linyi.posemon

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ChatgptAPI(private val apiKey: String) {

    private val client = OkHttpClient()
    private val url = "https://api.dify.ai/v1/chat-messages" // 正確的 API 終端點
    private var context = ""

    fun sendMessage(query: String, callback: (String) -> Unit) {
        context += "\nUser: $query" // 更新上下文
        val json = JSONObject().apply {
            put("query", context) // 傳遞整個上下文
            put("query", query)
            put("inputs", JSONObject()) // 如果沒有自定義參數，保持空
            put("streaming", "blocking")
            put("model", "gpt-4-mini")
            put("user", "unique_user_id") // 替換為您的唯一用戶 ID
            put("temperature", 0.99) // 增加生成隨機性
            put("top_p", 0.95)     // 增加詞彙分佈的多樣性
        }

        // 將 JSON 轉換為請求主體
        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        Log.d("API_REQUEST", "Request Body: $json")

        // 建立 HTTP 請求
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey") // 添加 API Key
            .addHeader("Content-Type", "application/json")
            .build()

        // 發送請求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("請求失敗：${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("API_RESPONSE", "Response Code: ${response.code}")
                Log.d("API_RESPONSE", "Response Body: $responseBody")

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    callback("API 請求失敗，狀態碼：${response.code}，內容：$responseBody")
                    return
                }

                try {
                    val jsonObject = JSONObject(responseBody)
                    val answer = jsonObject.optString("answer", "無法取得回答")
                    context += "\nAI: $answer" // 更新上下文
                    callback(answer.trim())
                } catch (e: Exception) {
                    Log.e("API_PARSE_ERROR", "無法解析回應：${e.message}")
                    callback("伺服器回應格式無法解析")
                }
            }
        })
    }
}
