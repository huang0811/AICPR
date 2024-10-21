package lyi.linyi.posemon

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class ProfileActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var etName: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvEmail: TextView
    private lateinit var btnSave: ImageButton
    private lateinit var btnEdit: ImageButton
    private lateinit var btnLogout: ImageButton
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 初始化 Firebase Auth
        mAuth = FirebaseAuth.getInstance()

        // 初始化 GoogleSignInClient
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // 綁定 UI 元素
        etName = findViewById(R.id.et_name)
        etPassword = findViewById(R.id.et_password)
        tvEmail = findViewById(R.id.tv_email)
        btnSave = findViewById(R.id.btn_save)
        btnEdit = findViewById(R.id.btn_edit)
        btnLogout = findViewById(R.id.btn_logout)
        btnBack = findViewById(R.id.btn_garyback)

        // 顯示當前用戶資料
        val user = mAuth.currentUser
        user?.let {
            // 設置電子郵件
            tvEmail.text = it.email
            // 設置顯示名稱（姓名）作為 hint
            etName.hint = it.displayName ?: "No Name"
            // 設置密碼的 hint 為占位符
            etPassword.hint = "********"
        }

        // 編輯按鈕點擊事件
        btnEdit.setOnClickListener {
            etName.isEnabled = true
            etPassword.isEnabled = true
            btnSave.visibility = View.VISIBLE
            btnEdit.visibility = View.GONE
        }

        // 保存按鈕點擊事件
        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newPassword = etPassword.text.toString().trim()

            if (newName.isNotEmpty()) {
                updateUserName(newName)
            }

            if (newPassword.isNotEmpty()) {
                if (newPassword.length >= 6) {
                    updateUserPassword(newPassword)
                } else {
                    Toast.makeText(this, "密碼至少需要6個字符", Toast.LENGTH_SHORT).show()
                }
            }

            etName.isEnabled = false
            etPassword.isEnabled = false
            btnSave.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
        }

        // 登出按鈕點擊事件
        btnLogout.setOnClickListener {
            logout()
        }

        // 返回按鈕點擊事件
        btnBack.setOnClickListener {
            finish()
        }
    }

    // 更新用戶姓名
    private fun updateUserName(newName: String) {
        val user = mAuth.currentUser
        user?.let {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            it.updateProfile(profileUpdates)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "姓名更新成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "姓名更新失敗: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    // 更新用戶密碼
    private fun updateUserPassword(newPassword: String) {
        val user = mAuth.currentUser
        user?.let {
            it.updatePassword(newPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "密碼更新成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "密碼更新失敗: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    // 登出方法
    private fun logout() {
        mAuth.signOut()

        // 如果是 Google 登入，登出 Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this) {
            val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
            with(sharedPref.edit()) {
                clear()
                apply()
            }
            // 確保成功登出 Google 後執行下一步
            Toast.makeText(this, "已成功登出", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
