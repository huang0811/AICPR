package lyi.linyi.posemon

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GoogleActivity"
    }

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var resultTextView: TextView
    private lateinit var loginButton: ImageButton
    private lateinit var googleSignInButton: ImageButton
    private lateinit var signupButton: ImageButton
    private lateinit var forgotPasswordButton: ImageButton
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private lateinit var logoutButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance()
        resultTextView = findViewById(R.id.textView_Result)
        loginButton = findViewById(R.id.btn_login)
        googleSignInButton = findViewById(R.id.btn_google_signin)
        signupButton = findViewById(R.id.btn_signup)
        forgotPasswordButton = findViewById(R.id.btn_forgotPassword)
        logoutButton = findViewById(R.id.btn_logout)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize the ActivityResultLauncher for sign-in
        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    Log.d(TAG, "firebaseAuthWithGoogle:" + account?.id)
                    account?.idToken?.let { firebaseAuthWithGoogle(it) }
                } catch (e: ApiException) {
                    Log.w(TAG, "Google sign in failed", e)
                    updateUI(null)
                }
            }
        }

        googleSignInButton.setOnClickListener {
            // 清除 Google 登入狀態以便每次都能重新選擇帳號
            mGoogleSignInClient.signOut().addOnCompleteListener {
                googleSignIn()
            }
        }

        loginButton.setOnClickListener {
            // 獲取使用者輸入的電子郵件和密碼
            val email = findViewById<EditText>(R.id.emailInput).text.toString().trim()
            val password = findViewById<EditText>(R.id.passwordInput).text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = mAuth.currentUser
                            val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)

                            // 獲取使用者的名稱，預設使用 Firebase 的 displayName，如果有自定義名稱則使用自定義名稱
                            val userName = sharedPref.getString("userName", user?.displayName ?: "Unknown")

                            with(sharedPref.edit()) {
                                putString("userID", user?.uid)
                                putString("userName", userName)
                                putBoolean("isLoggedIn", true)
                                apply()
                            }

                            Toast.makeText(this, "登入成功，歡迎 $userName", Toast.LENGTH_SHORT).show()

                            // 登入成功後跳轉到 SelectActivity
                            startActivity(Intent(this, SelectActivity::class.java))
                            finish()
                        } else {
                            when (task.exception) {
                                is FirebaseAuthInvalidCredentialsException -> {
                                    Toast.makeText(this, "密碼錯誤，請重試", Toast.LENGTH_SHORT).show()
                                }
                                is FirebaseAuthInvalidUserException -> {
                                    Toast.makeText(this, "用戶不存在，請註冊", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    Toast.makeText(this, "登入失敗: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            updateUI(null)
                        }
                    }
            } else {
                Toast.makeText(this, "請輸入電子郵件和密碼", Toast.LENGTH_SHORT).show()
            }
        }

        signupButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }

        forgotPasswordButton.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
            finish()
        }

        logoutButton.setOnClickListener {
            signOut()
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = mAuth.currentUser
        updateUI(currentUser)
    }

    private fun googleSignIn() {
        val signInIntent = mGoogleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = mAuth.currentUser

                    val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("userID", user?.uid)
                        putString("userName", user?.displayName ?: user?.email)
                        putBoolean("isLoggedIn", true)
                        apply()
                    }

                    updateUI(user)

                    // 成功後跳轉到 SelectActivity
                    startActivity(Intent(this, SelectActivity::class.java))
                    finish()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Firebase Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    updateUI(null)
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        resultTextView.text = if (user != null) {
            "Logged in as: ${user.email}"
        } else {
            "未登入"
        }
    }

    private fun signOut() {
        mAuth.signOut()
        mGoogleSignInClient.signOut().addOnCompleteListener(this) {
            val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
            with(sharedPref.edit()) {
                clear()
                apply()
            }
            updateUI(null)
            Toast.makeText(this, "已成功登出", Toast.LENGTH_SHORT).show()
        }
    }
}
