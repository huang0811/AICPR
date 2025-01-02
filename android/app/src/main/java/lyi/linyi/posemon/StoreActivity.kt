package lyi.linyi.posemon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StoreActivity : AppCompatActivity() {

    private lateinit var rvProducts: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var tvUserPoints: TextView
    private val db = FirebaseFirestore.getInstance()
    private var userPoints: Long = 0 // 紀錄用戶的積分

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        // 初始化 UI 元素
        rvProducts = findViewById(R.id.rv_products)
        btnBack = findViewById(R.id.btn_back)
        tvUserPoints = findViewById(R.id.user_points)

        // 返回按鈕
        btnBack.setOnClickListener {
            finish()
        }

        // 設置 RecyclerView
        rvProducts.layoutManager = GridLayoutManager(this, 3) // 三列網格佈局

        // 加載用戶積分和商品列表
        loadUserPoints()
        loadProducts()
    }

    // 加載用戶積分
    private fun loadUserPoints() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            db.collection("user_points").document(userId).get()
                .addOnSuccessListener { document ->
                    userPoints = document.getLong("totalPoints") ?: 0
                    tvUserPoints.text = " $userPoints"
                }
                .addOnFailureListener {
                    Toast.makeText(this, "無法加載積分", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 加載商品列表
    private fun loadProducts() {
        db.collection("products").get()
            .addOnSuccessListener { querySnapshot ->
                val products = querySnapshot.documents.map { it.data!! }
                products.forEach { product ->
                    println("Product: $product") // 輸出每個商品
                }
                rvProducts.adapter = ProductAdapter(products, userPoints) { product, position ->
                    redeemProduct(product, position)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "無法加載商品", Toast.LENGTH_SHORT).show()
            }
    }

    // 商品兌換邏輯
    private fun redeemProduct(product: Map<String, Any>, position: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val productName = product["name"] as String
        val productPrice = (product["price"] as Long)

        if (userPoints >= productPrice) {
            // 扣除積分
            userPoints -= productPrice
            tvUserPoints.text = "$userPoints"

            // 更新 Firebase 積分
            userId?.let {
                db.collection("user_points").document(it)
                    .update("totalPoints", userPoints)
                    .addOnSuccessListener {
                        Toast.makeText(this, "成功兌換：$productName", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "兌換失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Toast.makeText(this, "積分不足！", Toast.LENGTH_SHORT).show()
        }
    }
}

// 商品適配器
class ProductAdapter(
    private val products: List<Map<String, Any>>,
    private var userPoints: Long,
    private val onRedeem: (Map<String, Any>, Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProductImage: ImageView = itemView.findViewById(R.id.iv_product_image)
        val tvProductPrice: TextView = itemView.findViewById(R.id.tv_product_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        val price = (product["price"] as Long).toInt()
        val imageResName = product["imageRes"] as? String
        val imageUrl = product["imageUrl"] as? String

        // 設置商品價格
        holder.tvProductPrice.text = price.toString()

        // 根據 imageRes 或 imageUrl 加載圖片
        if (imageResName != null) {
            val resId = holder.itemView.context.resources.getIdentifier(
                imageResName, "drawable", holder.itemView.context.packageName
            )
            holder.ivProductImage.setImageResource(resId)
        } else if (imageUrl != null) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_fries)
                .into(holder.ivProductImage)
        }

        // 點擊商品進行兌換
        holder.itemView.setOnClickListener {
            onRedeem(product, position)
        }
    }

    override fun getItemCount(): Int = products.size
}