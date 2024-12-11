package lyi.linyi.posemon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AchievementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_achievement)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化成就列表
        val achievements = mutableListOf(
            Achievement(1, "累積登入100天", "每日上線，累計100天", 100, 25, 100, false, false),
            Achievement(2, "累積登入50天", "每日上線，累計50天", 50, 50, 50, true, false),
            Achievement(3, "對戰成功50次", "完成對戰模式成功50次", 50, 30, 100, false, false),
            Achievement(4, "第一次完成訓練模式", "完成一次訓練模式", 1, 0, 10, false, false)
        )

        // 初始化 RecyclerView 和 Adapter
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_achievements)
        val adapter = AchievementAdapter(achievements) { achievement ->
            if (achievement.isCompleted && !achievement.isClaimed) {
                achievement.isClaimed = true
                Toast.makeText(this, "領取成功！獎勵：${achievement.reward} 金幣", Toast.LENGTH_SHORT).show()

                // 將已領取的成就沉底並刷新列表
                achievements.sortWith(compareBy({ it.isClaimed }, { !it.isCompleted }))
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 檢查成就完成狀態
        achievements.forEach { achievement ->
            achievement.isCompleted = achievement.progress >= achievement.target
        }
    }
}

class AchievementAdapter(
    private val achievements: List<Achievement>,
    private val onClaimClick: (Achievement) -> Unit
) : RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_achievement_name)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_achievement)
//        val tvProgress: TextView = itemView.findViewById(R.id.tv_achievement_progress)
//        val btnClaim: Button = itemView.findViewById(R.id.btn_claim)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_achievement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val achievement = achievements[position]

        // 更新成就名稱與進度
        holder.tvName.text = achievement.name
//        holder.tvProgress.text = "${achievement.progress}/${achievement.target}"

        // 更新進度條
        holder.progressBar.max = achievement.target
        holder.progressBar.progress = achievement.progress

//        // 更新按鈕狀態
//        holder.btnClaim.isEnabled = achievement.isCompleted && !achievement.isClaimed
//        holder.btnClaim.text = if (achievement.isClaimed) "已領取" else "領取"
//
//        // 按鈕點擊事件
//        holder.btnClaim.setOnClickListener {
//            onClaimClick(achievement)
//        }
    }

    override fun getItemCount(): Int = achievements.size
}

data class Achievement(
    val id: Int,               // 成就 ID
    val name: String,          // 成就名稱
    val description: String,   // 成就描述
    val target: Int,           // 完成目標
    var progress: Int,         // 當前進度
    val reward: Int,           // 獎勵數量
    var isCompleted: Boolean,  // 是否已完成
    var isClaimed: Boolean     // 是否已領取
)
