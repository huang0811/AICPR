package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // 初始化 Firebase Auth
        mAuth = FirebaseAuth.getInstance()

        // 綁定 UI 元素
        val emailEditText: EditText = findViewById(R.id.et_email)
        val sendButton: ImageButton = findViewById(R.id.btn_send)
        val noAccountButton: ImageView = findViewById(R.id.btn_no_account)

        // 發送重置密碼按鈕點擊事件
        sendButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "請輸入有效的電子郵件地址", Toast.LENGTH_SHORT).show()
            } else {
                sendPasswordResetEmail(email)
            }
        }

        // 點擊 "Don't have an account? Sign Up" 跳轉到註冊頁面
        noAccountButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "若$email 已註冊，我們將會發送重置密碼的郵件", Toast.LENGTH_SHORT).show()
                    // 返回到登入頁面
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "重置密碼失敗: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
