package lyi.linyi.posemon

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AchievementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btn_back: ImageButton
    private lateinit var btn_store: ImageButton
    private val db = FirebaseFirestore.getInstance()
    private lateinit var achievementAdapter: AchievementAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievement)

        // 初始化 UI
        recyclerView = findViewById(R.id.recycler_achievements)
        btn_back = findViewById(R.id.btn_back)
        btn_store = findViewById(R.id.btn_store)

        btn_back.setOnClickListener {
            val intent = Intent(this, SelectActivity::class.java)
            startActivity(intent)
            finish()
        }

        btn_store.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            startActivity(intent)
            finish()
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            loadAchievements(userId)
        } else {
            Toast.makeText(this, "未登入，無法加載成就", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAchievements(userId: String) {
        val achievementsRef = db.collection("user_achievements").document(userId).collection("achievements")
        achievementsRef.get()
            .addOnSuccessListener { querySnapshot ->
                val achievements = querySnapshot.documents.mapNotNull { it.data }.toMutableList()
                if (achievements.isEmpty()) {
                    initializeAchievements(userId)
                } else {
                    // 計算進度後再顯示
                    calculateAchievementsProgress(userId, achievements)
                }
            }
            .addOnFailureListener { e ->
                Log.e("AchievementActivity", "加載成就失敗：${e.message}")
                Toast.makeText(this, "加載成就失敗", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateAchievementsProgress(userId: String, achievements: List<Map<String, Any>>) {
        val userHistoryRef = db.collection("user_history")
        userHistoryRef.whereEqualTo("userID", userId).whereEqualTo("mode", "normal")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val normalModeCount = querySnapshot.size()

                val achievementsRef = db.collection("user_achievements").document(userId).collection("achievements")
                val batch = db.batch()

                val updatedAchievements = achievements.map { achievement ->
                    val goal = (achievement["goal"] as? Long ?: 0).toInt()
                    val currentProgress = normalModeCount

                    achievement.toMutableMap().apply {
                        this["current_progress"] = currentProgress
                        this["is_completed"] = currentProgress >= goal

                        val achievementId = this["id"] as? String ?: return@apply
                        val docRef = achievementsRef.document(achievementId)
                        batch.set(docRef, this)
                    }
                }

                batch.commit()
                    .addOnSuccessListener {
                        displayAchievements(updatedAchievements, userId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("AchievementActivity", "批量更新失敗：${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AchievementActivity", "查詢使用者歷史失敗：${e.message}")
            }
    }

    private fun initializeAchievements(userId: String) {
        val defaultAchievements = listOf(
            mapOf(
                "id" to "achievement_1",
                "name" to "完成一次一般模式",
                "goal" to 1,
                "current_progress" to 0,
                "reward" to 10,
                "is_completed" to false,
                "is_claimed" to false
            ),
            mapOf(
                "id" to "achievement_2",
                "name" to "完成五次一般模式",
                "goal" to 5,
                "current_progress" to 0,
                "reward" to 50,
                "is_completed" to false,
                "is_claimed" to false
            )
        )

        val achievementsRef = db.collection("user_achievements").document(userId).collection("achievements")
        val batch = db.batch()
        for (achievement in defaultAchievements) {
            val docId = achievement["id"] as String
            val docRef = achievementsRef.document(docId)
            batch.set(docRef, achievement)
        }

        batch.commit()
            .addOnSuccessListener {
                loadAchievements(userId)
            }
            .addOnFailureListener { e ->
                Log.e("AchievementActivity", "初始化成就失敗：${e.message}")
                Toast.makeText(this, "初始化成就失敗", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayAchievements(achievements: List<Map<String, Any>>, userId: String) {
        val sortedAchievements = achievements.sortedWith(compareBy(
            { !(it["is_completed"] as? Boolean ?: false) },
            { it["is_claimed"] as? Boolean ?: false }
        )).toMutableList()

        achievementAdapter = AchievementAdapter(sortedAchievements, userId) { position ->
            recyclerView.postDelayed({
                achievementAdapter.notifyItemChanged(position)
            }, 200)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = achievementAdapter
    }
}

class AchievementAdapter(
    private val achievements: MutableList<Map<String, Any>>,
    private val userId: String,
    private val onAchievementClaimed: (Int) -> Unit // 回調，用於通知 Activity 刷新
) : RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tv_achievement_name)
        val progress: TextView = itemView.findViewById(R.id.calculate)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_achievement)
        val background: View = itemView.findViewById(R.id.achievementBackground)
        val reward: TextView = itemView.findViewById(R.id.tv_reward)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val achievement = achievements[position]
        val name = achievement["name"] as? String ?: "未知成就"
        val goal = (achievement["goal"] as? Long ?: 0).toInt()
        val currentProgress = (achievement["current_progress"] as? Int ?: 0)
        val isCompleted = achievement["is_completed"] as? Boolean ?: false
        val isClaimed = achievement["is_claimed"] as? Boolean ?: false
        val reward = (achievement["reward"] as? Long ?: 0).toInt()

        // 設置成就名稱和進度
        holder.name.text = name
        holder.progress.text = "$currentProgress/$goal"
        holder.progressBar.max = goal
        holder.progressBar.progress = currentProgress
        holder.reward.text = "x$reward"

        // 根據成就完成和領取狀態更新 UI
        if (isCompleted && !isClaimed) {
            holder.background.setBackgroundResource(R.drawable.ic_btnback_active) // 可領取
            holder.background.setOnClickListener {
                claimAchievement(holder.itemView.context, userId, achievement, holder.adapterPosition)
            }
            holder.itemView.alpha = 1.0f // 還原透明度
        } else if (isClaimed) {
            holder.itemView.alpha = 0.5f // 整個項目變半透明
            holder.background.setOnClickListener(null)
        } else {
            holder.background.setOnClickListener(null)
            holder.itemView.alpha = 1.0f // 預設還原透明度
        }
    }

    override fun getItemCount() = achievements.size

    private fun claimAchievement(context: Context, userId: String, achievement: Map<String, Any>, position: Int) {
        val achievementId = achievement["id"] as? String ?: return
        val reward = (achievement["reward"] as? Long ?: 0).toInt()

        val achievementsRef = FirebaseFirestore.getInstance()
            .collection("user_achievements").document(userId).collection("achievements").document(achievementId)

        achievementsRef.update("is_claimed", true)
            .addOnSuccessListener {
                achievements[position] = achievement.toMutableMap().apply {
                    this["is_claimed"] = true
                }
                onAchievementClaimed(position)
                updateUserTotalPoints(userId, reward)
                Toast.makeText(context, "成功領取成就獎勵！+ $reward 積分", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("AchievementAdapter", "領取成就失敗：${e.message}")
                Toast.makeText(context, "領取成就失敗，請稍後再試", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserTotalPoints(userId: String, pointsToAdd: Int) {
        val userPointsRef = FirebaseFirestore.getInstance()
            .collection("user_points").document(userId)

        userPointsRef.get()
            .addOnSuccessListener { document ->
                val currentPoints = document.getLong("totalPoints") ?: 0
                val newTotalPoints = currentPoints + pointsToAdd

                userPointsRef.set(mapOf("totalPoints" to newTotalPoints))
                    .addOnSuccessListener {
                        Log.d("UpdatePoints", "總積分更新成功：$newTotalPoints")
                    }
                    .addOnFailureListener { e ->
                        Log.e("UpdatePoints", "總積分更新失敗：${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("UpdatePoints", "獲取用戶積分失敗：${e.message}")
            }
    }
}