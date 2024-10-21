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
import com.google.firebase.database.ValueEventListener
import java.util.*
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context

class MatchActivity : AppCompatActivity() {

    // Firebase 相關變數
    private lateinit var database: FirebaseDatabase
    private lateinit var matchRef: DatabaseReference
    private var userId: String? = null
    private var playerName: String? = "Player" // 預設名稱
    private var isPlayer1: Boolean = true // 預設當前使用者是 Player1
    private var isMatched: Boolean = false // 紀錄是否已完成配對

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
        }

        // 綁定心臟 ImageView 並設置心跳動畫
        val heartImageView = findViewById<ImageView>(R.id.heart)
        startHeartBeatAnimation(heartImageView)
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
        // 生成一個唯一的房間ID或使用已存在的房間
        val roomId = generateOrJoinRoomId()
        matchRef = database.getReference("matches").child(roomId)

        // 設置當前玩家為 Player1，並保存用戶名稱
        matchRef.child("player1").setValue(userId)
        matchRef.child("player1Name").setValue(playerName)

        // 監聽匹配狀態
        matchRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val player2Id = snapshot.child("player2").getValue(String::class.java)
                val player2Name = snapshot.child("player2Name").getValue(String::class.java) ?: "Opponent"

                // 若成功找到對手且是另一位玩家
                if (player2Id != null && player2Id != userId && !isMatched) {
                    isMatched = true
                    // 更新 player2Name
                    matchRef.child("player2Name").setValue(player2Name)
                    startBattleActivity(isAiOpponent = false, opponentName = player2Name)
                } else if (player2Id == null && !isMatched) {
                    // 設置 player2 參數為當前用戶
                    matchRef.child("player2").setValue(userId)
                    matchRef.child("player2Name").setValue(playerName)
                }
            }

            override fun onCancelled(error: DatabaseError) {
            // 取消匹配的處理
            }
        })

        // 設置配對等待時間為 10 秒
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isMatched) {  // 若未匹配到對手則標記為 AI 對手
                isMatched = true
                startBattleActivity(isAiOpponent = true)
            }
        }, 10000) // 10 秒配對等待
    }

    // 生成或加入房間ID
    private fun generateOrJoinRoomId(): String {
        var availableRoomId: String? = null

        // 查找是否存在可用房間
        database.getReference("matches").orderByChild("player2").equalTo(null)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (room in snapshot.children) {
                        availableRoomId = room.key
                        break
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        // 返回可用房間ID或創建新房間
        return availableRoomId ?: database.getReference("matches").push().key ?: UUID.randomUUID().toString()
    }

    // 開始 BattleActivity 並傳遞角色參數
    private fun startBattleActivity(isAiOpponent: Boolean, opponentName: String = "CPRMAN") {
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
        intent.putExtra("playerName", playerName)
        intent.putExtra("opponentName", opponentName)

        startActivity(intent)
        finish()
    }
}
