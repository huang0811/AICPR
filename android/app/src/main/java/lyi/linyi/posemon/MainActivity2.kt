package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val startButton = findViewById<ImageButton>(R.id.btn_start)
        startButton.setOnClickListener {
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
        }

        val tapToStartImageView: ImageView = findViewById(R.id.tap)

        // 創建 Alpha 動畫
        val blinkAnimation = AlphaAnimation(0.0f, 1.0f)
        blinkAnimation.duration = 500 // 設定閃爍速度（500ms）
        blinkAnimation.startOffset = 20
        blinkAnimation.repeatMode = Animation.REVERSE
        blinkAnimation.repeatCount = Animation.INFINITE

        // 將動畫應用到 ImageView
        tapToStartImageView.startAnimation(blinkAnimation)
    }
}


