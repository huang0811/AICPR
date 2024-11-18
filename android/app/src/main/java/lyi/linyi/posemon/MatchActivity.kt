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
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
            finish()
        }

        // 綁定返回主選單的按鈕
        val btHome = findViewById<ImageButton>(R.id.btHome)
        btHome.setOnClickListener {
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
            finish()
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

    // 取消匹配並清除 Firebase 中的匹配房間
    private fun cancelMatching() {
        removeMatchListener() // 移除監聽器
        roomId?.let {
            matchRef = database.getReference("matches").child(it)
            matchRef.removeValue() // 刪除匹配房間資料
        }
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
                val aiName = "CPRMAN"
                matchRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.child("player2").getValue(String::class.java) == null) {
                            currentData.child("player2").value = "AICPRisChampion"
                            currentData.child("player2Name").value = aiName
                            return Transaction.success(currentData)
                        }
                        return Transaction.abort()
                    }

                    override fun onComplete(databaseError: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed) {
//                            matchRef.removeEventListener(matchListener)  // 移除監聽器
                            removeMatchListener()  // 移除監聽器
                            startBattleActivity(isAiOpponent = true, myName = playerName ?: "Player", opponentName = aiName)                        }
                    }
                })
            }
        }, 10000) // 10 秒配對等待
    }

    private fun removeMatchListener() {
        matchListener?.let { matchRef.removeEventListener(it) }
    }

    // 生成或加入房間ID
    private fun generateOrJoinRoomId(callback: (String) -> Unit) {
        var availableRoomId: String? = null

        database.getReference("matches").orderByChild("player2").equalTo(null)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (room in snapshot.children) {
                        val player1Id = room.child("player1").getValue(String::class.java)
                        if (player1Id != null && player1Id != userId) {
                            availableRoomId = room.key
                            break
                        }
                    }
                    // 返回可用房間ID或創建新房間
                    callback(availableRoomId ?: database.getReference("matches").push().key ?: UUID.randomUUID().toString())
                }

                override fun onCancelled(error: DatabaseError) {}
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