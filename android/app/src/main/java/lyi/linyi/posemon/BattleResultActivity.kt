package lyi.linyi.posemon

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class BattleResultActivity : AppCompatActivity() {

    private lateinit var ibBack: ImageButton
    private lateinit var ibRestart: ImageButton
    private lateinit var backgroundImageView: ImageView // 使用 ImageView 來設定背景
    private lateinit var ibHelp: ImageButton // 新增的「ask for help」按鈕

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle_result)
        initUI()

        // 接收傳遞過來的對戰結果
        val result = intent.getStringExtra("result")

        // 根據對戰結果更新 UI
        updateUIBasedOnResult(result)
    }

    private fun initUI() {
        backgroundImageView = findViewById(R.id.backgroundImage)
        ibBack = findViewById(R.id.ibBack)
        ibRestart = findViewById(R.id.ibRestart)
        ibHelp = findViewById(R.id.ibHelp) // 初始化 ask for help 按鈕

        ibBack.setOnClickListener {
            startActivity(Intent(this, SelectActivity::class.java))
            finish()
        }

        ibRestart.setOnClickListener {
            startActivity(Intent(this, MatchActivity::class.java))
            finish()
        }

//        // ask for help 按鈕點擊事件
//        ibHelp.setOnClickListener {
//            // 撥放影片
//            playHelpVideo()
//        }
    }

    private fun updateUIBasedOnResult(result: String?) {
        // 根據比賽結果顯示勝利或失敗畫面
        if (result == "win") {
            backgroundImageView.setImageResource(R.drawable.ic_champion)
            // 隱藏 ask for help 按鈕
            ibHelp.visibility = ImageButton.GONE
        } else {
            backgroundImageView.setImageResource(R.drawable.ic_fail)
            // 顯示 ask for help 按鈕
            ibHelp.visibility = ImageButton.VISIBLE
        }
    }
}
