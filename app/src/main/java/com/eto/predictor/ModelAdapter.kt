package com.eto.predictor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ModelAdapter(
    private val models: List<ApiModel>,
    private val onClick: (ApiModel) -> Unit
) : RecyclerView.Adapter<ModelAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val rank: TextView = view.findViewById(R.id.tvRank)
        val modelId: TextView = view.findViewById(R.id.tvModelId)
        val features: TextView = view.findViewById(R.id.tvFeatures)
        val r2: TextView = view.findViewById(R.id.tvR2)
        val rmse: TextView = view.findViewById(R.id.tvRmse)
        val inputs: TextView = view.findViewById(R.id.tvInputs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_card, parent, false))

    override fun getItemCount() = models.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = models[position]
        holder.rank.text = "#${m.rank}"
        holder.modelId.text = m.features.replace(" + ", ", ")
        holder.r2.text = "R² ${m.r2_test}"
        holder.rmse.text = "RMSE ${m.rmse}"
        holder.inputs.text = "Inputs: ${m.n_inputs}"
        holder.itemView.setOnClickListener { onClick(m) }
    }

}
