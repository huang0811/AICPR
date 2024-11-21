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

    fun sendMessage(messages: List<ChatMessage>, round: Int, callback: (String) -> Unit) {
        try {
            val json = JSONObject()
            json.put("model", "gpt-3.5-turbo")

            val messagesArray = JSONArray()

            // 加入上下文的歷史訊息
            for (chatMessage in messages) {
                val jsonMessage = JSONObject()
                jsonMessage.put("role", if (chatMessage.isUser) "user" else "assistant")
                jsonMessage.put("content", chatMessage.message)
                messagesArray.put(jsonMessage)
            }

            // 設置系統訊息，確保對話符合指定的回合
            val systemMessage = JSONObject()
            systemMessage.put("role", "system")
            when (round) {
                1 -> systemMessage.put("content", "用自然的語氣介紹叫叫CABD，並引導用戶進入第一步的'叫'：確認患者有無意識和呼吸。")
                2 -> systemMessage.put("content", "用戶剛了解第一步，請用自然的語氣引導他們進入第二步的'叫'：呼叫他人並撥打119求救。")
                3 -> systemMessage.put("content", "介紹胸部按壓（C），包括按壓的位置、姿勢、深度和頻率。保持生動自然的對話。")
                4 -> systemMessage.put("content", "介紹呼吸道暢通（A），用簡單有趣的方式解釋如何打開呼吸道。")
                5 -> systemMessage.put("content", "簡單介紹人工呼吸（B），提及AED的用途，但保持簡短。")
                6 -> systemMessage.put("content", "鼓勵用戶進行實際練習，並用友善的語氣引導。")
                7 -> systemMessage.put("content", "總結叫叫CABD的步驟，並詢問用戶是否準備好進行練習。")
            }
            messagesArray.put(systemMessage)

            json.put("messages", messagesArray)
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("ChatgptAPI", "API request failed: ${e.message}")
                    callback("請求失敗，錯誤: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.d("ChatgptAPI", "API request failed, status code: ${response.code}")
                        callback("API 請求失敗，狀態碼: ${response.code}")
                    } else {
                        val responseBody = response.body?.string()
                        Log.d("ChatgptAPI", "API response: $responseBody")

                        try {
                            val jsonObject = JSONObject(responseBody)
                            val choicesArray = jsonObject.getJSONArray("choices")
                            val messageObject = choicesArray.getJSONObject(0).getJSONObject("message")
                            val content = messageObject.getString("content")
                            callback(content.trim())
                        } catch (e: Exception) {
                            Log.d("ChatgptAPI", "Failed to parse response: ${e.message}")
                            callback("無法解析回應，錯誤: ${e.message}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.d("ChatgptAPI", "Exception in sendMessage: ${e.message}")
            callback("請求發生錯誤，錯誤: ${e.message}")
        }
    }

    fun generateChoices(context: List<ChatMessage>, round: Int, callback: (List<String>) -> Unit) {
        try {
            val json = JSONObject()
            json.put("model", "gpt-3.5-turbo")

            val messagesArray = JSONArray()

            // 加入上下文的歷史訊息
            for (chatMessage in context) {
                val jsonMessage = JSONObject()
                jsonMessage.put("role", if (chatMessage.isUser) "user" else "assistant")
                jsonMessage.put("content", chatMessage.message)
                messagesArray.put(jsonMessage)
            }

            // 根據回合設置用戶選項的系統訊息
            val systemMessage = JSONObject()
            systemMessage.put("role", "system")
            when (round) {
                1 -> systemMessage.put("content", "生成用戶選項：'我想學CPR'、'如何按壓？'、'開始吧！'")
                2 -> systemMessage.put("content", "生成用戶選項：'了解了！'、'那下一步呢？'、'第二步是什麼？'")
                3 -> systemMessage.put("content", "生成用戶選項：'按壓要多深？'、'這樣對嗎？'、'怎麼按？'")
                4 -> systemMessage.put("content", "生成用戶選項：'如何打開呼吸道？'、'這樣簡單嗎？'、'我懂了！'")
                5 -> systemMessage.put("content", "生成用戶選項：'怎麼人工呼吸？'、'需要AED嗎？'、'還有什麼？'")
                6 -> systemMessage.put("content", "生成用戶選項：'我要練習'、'我準備好了'、'開始吧！'")
                7 -> systemMessage.put("content", "生成用戶選項：'進入練習'、'學完了'、'來吧！'")
            }
            messagesArray.put(systemMessage)

            json.put("messages", messagesArray)

            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("ChatgptAPI", "Failed to generate choices: ${e.message}")
                    callback(listOf("無法生成選項，錯誤: ${e.message}"))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.d("ChatgptAPI", "Failed to generate choices, status code: ${response.code}")
                        callback(listOf("無法生成選項，狀態碼: ${response.code}"))
                    } else {
                        val responseBody = response.body?.string()
                        Log.d("ChatgptAPI", "Choices generated: $responseBody")

                        try {
                            val jsonObject = JSONObject(responseBody)
                            val choicesArray = jsonObject.getJSONArray("choices")
                            val generatedText = choicesArray.getJSONObject(0).getJSONObject("message").getString("content")

                            // 解析生成的選項，並符合規範
                            val generatedChoices = generatedText.split(Regex("\\d+\\.")).map {
                                it.trim()
                            }.filter { it.isNotEmpty() && it.length <= 10 }

                            // 返回符合規範的選項
                            val finalChoices = generatedChoices.take(3)

                            callback(finalChoices)
                        } catch (e: Exception) {
                            Log.d("ChatgptAPI", "Failed to parse generated choices: ${e.message}")
                            callback(listOf("無法解析選項，錯誤: ${e.message}"))
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.d("ChatgptAPI", "Exception in generateChoices: ${e.message}")
            callback(listOf("請求發生錯誤，錯誤: ${e.message}"))
        }
    }
}
