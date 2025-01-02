package lyi.linyi.posemon

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.*
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.MutableData

class MatchActivity : AppCompatActivity() {

    // Firebase 相關變數
    private lateinit var database: FirebaseDatabase
    private lateinit var matchRef: DatabaseReference
    private var userId: String? = null
    private var playerName: String? = "Player" // 預設名稱
    private var isPlayer1: Boolean = true // 預設當前使用者是 Player1
    private var isMatched: Boolean = false // 紀錄是否已完成配對
    private var roomId: String? = null // 增加一個變數來保存房間ID
    private var matchListener: ValueEventListener? = null // 儲存配對監聽器

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match)

        // 初始化 Firebase
        database = FirebaseDatabase.getInstance()
        userId = getUserId()
        playerName = getUserDisplayName() ?: "Player"

        if (userId != null) {
            startMatching() // 開始配對邏輯
        } else {
            // 若未登入，跳轉到登入介面
            Toast.makeText(this, "請先登入才能對戰匹配！", Toast.LENGTH_LONG).show()
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
            finish()
        }

        // 綁定返回主選單的按鈕
        val btHome = findViewById<ImageButton>(R.id.btHome)
        btHome.setOnClickListener {
            showExitMatchDialog()
        }

        // 綁定心臟 ImageView 並設置心跳動畫
        val heartImageView = findViewById<ImageView>(R.id.heart)
        startHeartBeatAnimation(heartImageView)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelMatching() // 取消匹配並移除配對資料
    }

    override fun onPause() {
        super.onPause()
        cancelMatching() // 取消匹配並移除配對資料
    }

    // 顯示結束匹配確認對話框
    private fun showExitMatchDialog() {
        AlertDialog.Builder(this)
            .setTitle("結束匹配")
            .setMessage("確定要結束匹配對戰嗎？")
            .setPositiveButton("是") { _, _ ->
                // 結束匹配並返回主選單
                isMatched = true // 確保不會再觸發配對完成邏輯
                cancelMatching() // 清理 Firebase 配對資料
                val intent = Intent(this, SelectActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("否") { dialog, _ ->
                // 關閉對話框
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // 攔截返回鍵行為，顯示確認對話框
        showExitMatchDialog()
    }

    // 取消匹配並清除 Firebase 中的匹配房間
    private fun cancelMatching() {
        removeMatchListener() // 移除監聽器
        roomId?.let { roomId ->
            matchRef = database.getReference("matches").child(roomId)
            matchRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    // 如果當前用戶是 player1 或 player2，清除對應的節點
                    val existingPlayer1 = currentData.child("player1").getValue(String::class.java)
                    val existingPlayer2 = currentData.child("player2").getValue(String::class.java)

                    if (existingPlayer1 == userId) {
                        currentData.child("player1").value = null
                        currentData.child("player1Name").value = null
                    } else if (existingPlayer2 == userId) {
                        currentData.child("player2").value = null
                        currentData.child("player2Name").value = null
                    }

                    // 如果房間中沒有任何玩家，則刪除整個房間
                    if (currentData.child("player1").value == null && currentData.child("player2").value == null) {
                        currentData.value = null // 將節點設置為空，刪除該節點
                    }

                    return Transaction.success(currentData) // 返回更新後的數據
                }

                override fun onComplete(databaseError: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (databaseError != null) {
                        // 錯誤處理：刪除匹配資料失敗
                        Toast.makeText(this@MatchActivity, "取消匹配時發生錯誤：${databaseError.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        isMatched = true // 確保不會再進行配對完成的邏輯
    }

    // 心跳動畫的函式
    private fun startHeartBeatAnimation(heartImageView: ImageView) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.2f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.2f, 1.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(heartImageView, scaleX, scaleY)
        animator.duration = 1200 // 每次心跳的時間
        animator.repeatCount = ObjectAnimator.INFINITE // 無限重複
        animator.repeatMode = ObjectAnimator.REVERSE // 動畫來回反轉
        animator.interpolator = LinearInterpolator() // 線性插值器
        animator.start()
    }

    // 獲取當前登入用戶的ID
    private fun getUserId(): String? {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.uid
    }

    private fun getUserDisplayName(): String? {
        val username = FirebaseAuth.getInstance().currentUser
        return username?.displayName
    }

    // 開始配對邏輯
    private fun startMatching() {
        generateOrJoinRoomId { roomIdResult ->
            roomId = roomIdResult
            matchRef = database.getReference("matches").child(roomId!!)

            // 嘗試配對：先檢查房間中是否有 Player1
            matchRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val existingPlayer1 = currentData.child("player1").getValue(String::class.java)
                    return if (existingPlayer1 == null) {
                        // 當前用戶成為 player1
                        currentData.child("player1").value = userId
                        currentData.child("player1Name").value = playerName
                        Transaction.success(currentData)
                    } else if (existingPlayer1 != userId && currentData.child("player2").getValue(String::class.java) == null) {
                        // 當前用戶成為 player2
                        currentData.child("player2").value = userId
                        currentData.child("player2Name").value = playerName
                        Transaction.success(currentData)
                    } else {
                        Transaction.abort()
                    }
                }

                override fun onComplete(databaseError: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (committed) {
                        monitorMatchStatus()
                    } else {
                        // 配對失敗，可考慮重試或其他處理方式
                        startMatching() // 重新嘗試配對
                    }
                }
            })
        }
    }

    private fun monitorMatchStatus() {
        val matchListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val player1Id = snapshot.child("player1").getValue(String::class.java)
                val player2Id = snapshot.child("player2").getValue(String::class.java)

                val player1Name = snapshot.child("player1Name").getValue(String::class.java) ?: "Player 1"
                val player2Name = snapshot.child("player2Name").getValue(String::class.java) ?: "Opponent"

                if (player1Id != null && player2Id != null) {
                    val myName = if (userId == player1Id) player1Name else player2Name
                    val opponentName = if (userId == player1Id) player2Name else player1Name

                    if (!isMatched) {
                        isMatched = true
//                        matchRef.removeEventListener(this)  // 移除監聽器
                        removeMatchListener()  // 移除監聽器
                        startBattleActivity(isAiOpponent = false, myName = myName, opponentName = opponentName)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // 處理取消的情況
            }
        }

        matchRef.addValueEventListener(matchListener)

        // 設置配對等待時間為 10 秒
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isMatched) {
                isMatched = true
                showMatchOptionDialog()

            }
        }, 10000) // 10 秒配對等待
    }

    private fun showMatchOptionDialog() {
        val aiName = "CPRMAN"
        val aiUserId = "AICPRisChampion" // 固定 AI 的 userID

        AlertDialog.Builder(this)
            .setTitle("匹配未完成")
            .setMessage("您可以選擇繼續匹配或直接與 AI 對戰。")
            .setPositiveButton("繼續匹配") { _, _ ->
                isMatched = false
                startMatching() // 繼續匹配
            }
            .setNegativeButton("與AI對戰") { _, _ ->
                matchRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.child("player2").getValue(String::class.java) == null) {
                            // 將 AI 加入到匹配房間中
                            currentData.child("player2").value = aiUserId
                            currentData.child("player2Name").value = aiName
                            return Transaction.success(currentData)
                        }
                        return Transaction.abort()
                    }

                    override fun onComplete(databaseError: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed) {
                            // 如果 AI 成功加入匹配房間，開始對戰
                            removeMatchListener()
                            startBattleActivity(isAiOpponent = true, myName = playerName ?: "Player", opponentName = aiName)
                        } else {
                            // 如果房間更新失敗，可以選擇重試或其他操作
                            Toast.makeText(this@MatchActivity, "與 AI 配對失敗，請重試。", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            .setCancelable(false)
            .show()
    }

    private fun removeMatchListener() {
        matchListener?.let { matchRef.removeEventListener(it) }
    }

    // 生成或加入房間ID
    private fun generateOrJoinRoomId(callback: (String) -> Unit) {
        database.getReference("matches").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var availableRoomId: String? = null

                for (room in snapshot.children) {
                    val player1Id = room.child("player1").getValue(String::class.java)
                    val player2Id = room.child("player2").getValue(String::class.java)

                    // 過濾只有 player1 且尚未匹配 player2 的房間
                    if (player1Id != null && player2Id == null && player1Id != userId) {
                        availableRoomId = room.key
                        break
                    }

                    // 如果房間中沒有任何有效玩家，刪除該房間
                    if (player1Id == null && player2Id == null) {
                        room.ref.removeValue()
                    }
                }

                // 如果沒有可用房間，創建新房間
                callback(availableRoomId ?: database.getReference("matches").push().key!!)
            }

            override fun onCancelled(error: DatabaseError) {
                // 處理取消事件
            }
        })
    }

    // 開始 BattleActivity 並傳遞角色參數
    private fun startBattleActivity(isAiOpponent: Boolean, myName: String, opponentName: String) {
        val myName = playerName ?: "Player"  // 確保名稱不為 null
        // 震動邏輯
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            // 設定震動模式：持續 500 毫秒
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(300) // For devices below Android O
            }
        }

        // 準備跳轉到 BattleActivity
        val intent = Intent(this, BattleActivity::class.java)
        intent.putExtra("isAiOpponent", isAiOpponent)
        intent.putExtra("isPlayer1", isPlayer1)
        intent.putExtra("playerName", myName)
        intent.putExtra("opponentName", opponentName)
        intent.putExtra("roomId", roomId)

        startActivity(intent)
        finish()
    }
}