package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SelectActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select)

        // 初始化 Firebase Auth
        mAuth = FirebaseAuth.getInstance()

        // 綁定按鈕
        val loginButton = findViewById<ImageButton>(R.id.login)
        val profileButton = findViewById<ImageButton>(R.id.profile)
        val achievementButton = findViewById<ImageButton>(R.id.achievement)

        // 根據用戶是否登入顯示相應的按鈕
        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            // 用戶已登入，顯示 profile 和 achievement，隱藏 login
            loginButton.visibility = View.GONE
            profileButton.visibility = View.VISIBLE
            achievementButton.visibility = View.VISIBLE
        } else {
            // 用戶未登入，顯示 login，隱藏 profile 和 achievement
            loginButton.visibility = View.VISIBLE
            profileButton.visibility = View.GONE
            achievementButton.visibility = View.GONE
        }

        // 設置按鈕點擊事件
        val classic = findViewById<ImageButton>(R.id.normal_mode)
        classic.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        val battle = findViewById<ImageButton>(R.id.battle_mode)
        battle.setOnClickListener {
            val intent = Intent(this, MatchActivity::class.java)
            startActivity(intent)
        }

        val history = findViewById<ImageButton>(R.id.history)
        history.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        loginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
//
//        achievementButton.setOnClickListener {
//            val intent = Intent(this, AchievementActivity::class.java) // 假設你有一個 AchievementActivity
//            startActivity(intent)huang0811
//        }
    }
}
