package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 初始化 Firebase Auth
        mAuth = FirebaseAuth.getInstance()

        // 綁定 UI 元素
        val usernameEditText: EditText = findViewById(R.id.et_username)
        val emailEditText: EditText = findViewById(R.id.et_email)
        val passwordEditText: EditText = findViewById(R.id.et_password)
        val signUpButton: ImageButton = findViewById(R.id.btn_signup)
        val loginButton: ImageButton = findViewById(R.id.btn_account)

        // 註冊按鈕點擊事件
        signUpButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val username = usernameEditText.text.toString().trim() // 新增的用戶名輸入框

            // 驗證輸入
            if (password.length < 6) {
                Toast.makeText(this, "密碼至少需要6個字符", Toast.LENGTH_SHORT).show()
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "請輸入有效的電子郵件地址", Toast.LENGTH_SHORT).show()
            } else if (username.isEmpty()) {
                Toast.makeText(this, "請輸入用戶名", Toast.LENGTH_SHORT).show()
            } else {
                // 如果所有驗證都通過，呼叫註冊函數
                registerUser(email, password, username)
            }
        }

        // 點擊 "Have an account" 按鈕，跳轉到登入頁面
        loginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun registerUser(email: String, password: String, username: String) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = mAuth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                Toast.makeText(this, "註冊成功，歡迎 $username", Toast.LENGTH_SHORT).show()

                                // 將用戶名儲存到 SharedPreferences
                                val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
                                with(sharedPref.edit()) {
                                    putString("userID", user.uid)
                                    putString("userName", username)
                                    putBoolean("isLoggedIn", true)
                                    apply()
                                }

                                // 跳轉到登入頁面
                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this, "更新用戶名失敗", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "註冊失敗: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

}
