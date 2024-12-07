package lyi.linyi.posemon

import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ChatgptAPI(private val apiKey: String) {

    private val client = OkHttpClient()
    private val url = "https://api.openai.com/v1/chat/completions"

    // 修改方法以接受 userChoice 並基於選擇生成回覆
    fun sendMessage(messages: List<ChatMessage>, round: Int, userChoice: String, callback: (String) -> Unit) {
        try {
            val json = JSONObject()
            json.put("model", "gpt-3.5-turbo")

            val messagesArray = JSONArray()

            // 添加歷史對話
            for (chatMessage in messages) {
                val jsonMessage = JSONObject()
                jsonMessage.put("role", if (chatMessage.isUser) "user" else "assistant")
                jsonMessage.put("content", chatMessage.message)
                messagesArray.put(jsonMessage)
            }

            // 添加系統提示，根據 userChoice 提供特定的 CPR 按壓知識
            val systemMessage = JSONObject().apply {
                put("role", "system")
                put(
                    "content", """
                    請根據以下用戶選擇的選項回覆 CPR 按壓相關的教學內容：
                    1. '為什麼要打直？'：解釋雙手完全打直的重要性以及如何正確操作，字數不超過50字。
                    2. '165 度怎麼量？'：按壓標準為雙手平均角度大於165度，說明按壓時角度為什麼不能超過 165 度，並給出調整建議，字數不超過0字。
                    3. '怎麼知道深度？'：解釋按壓深度的標準是 5-6 公分，並給出簡單測量方法，字數不超過50字。
                    4. '怎麼控制節奏？'：說明按壓頻率 100-120 下/分鐘的原因，並提供控制節奏的建議，或說明我們的應用偵測中有提供節拍器可以聆聽，字數不超過50字。
                    5. '節拍器怎麼用？'：指導用戶如何使用我們應用中的節拍器來輔助保持節奏，字數不超過50字。
                    
                    用戶選擇的選項是：'$userChoice'
                """.trimIndent()
                )
            }
            messagesArray.put(systemMessage)

            json.put("messages", messagesArray)

            // 構建請求
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback("請求失敗，錯誤：${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        callback("API 請求失敗，狀態碼：${response.code}")
                    } else {
                        val responseBody = response.body?.string()
                        try {
                            val jsonObject = JSONObject(responseBody ?: "")
                            val choicesArray = jsonObject.getJSONArray("choices")
                            val messageObject = choicesArray.getJSONObject(0).getJSONObject("message")
                            val content = messageObject.getString("content")
                            callback(content.trim())
                        } catch (e: Exception) {
                            callback("無法解析回應，錯誤：${e.message}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            callback("請求發生錯誤，錯誤：${e.message}")
        }
    }

    // 選項生成保持不變
    fun generateChoices(context: List<ChatMessage>, round: Int, callback: (List<String>) -> Unit) {
        val choices = when (round) {
            1 -> listOf("為什麼要打直？", "彎曲會怎樣？", "了解了")
            2 -> listOf("165 度怎麼量？", "為什麼要這樣？", "試試看")
            3 -> listOf("怎麼知道深度？", "太淺有什麼影響？", "明白了")
            4 -> listOf("怎麼控制節奏？", "這樣慢嗎？", "學到了")
            5 -> listOf("節拍器怎麼用？", "語音回饋是什麼？", "開始吧")
            6 -> listOf("再說一次", "我準備好了", "試試看")
            7 -> listOf("開始練習", "直接開始練習吧", "我想開始練習了")
            else -> {
                Log.e("ChatgptAPI", "未知回合數：$round")
                listOf("錯誤：未知回合")
            }
        }
        callback(choices)
    }
}