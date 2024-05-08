package lyi.linyi.posemon

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton

class SelectActivity : AppCompatActivity() {
    private lateinit var classicButton: ImageButton // 更改变量名和类型
    private lateinit var battleButton: ImageButton // 更改变量名和类型
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select)
        val classic = findViewById<ImageButton>(R.id.classic)
        classic.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        val battle = findViewById<ImageButton>(R.id.battle)
        battle.setOnClickListener {
            val intent = Intent(this, BattleActivity::class.java)
            startActivity(intent)
        }
    }
}