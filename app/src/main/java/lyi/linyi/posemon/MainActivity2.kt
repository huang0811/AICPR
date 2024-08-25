package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity2 : AppCompatActivity() {
    private lateinit var btStart:Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val startButton = findViewById<ImageButton>(R.id.btn_start)
        startButton.setOnClickListener {
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
        }

//       btStart= findViewById(R.id.btStart)
//        btStart.setOnClickListener {
//            // 點擊按鈕時，啟動 MainActivity
//            val intent = Intent(this, MainActivity::class.java)
//            startActivity(intent)
//            // 關閉當前的 MainActivity2
//            finish()
//        }
    }
}