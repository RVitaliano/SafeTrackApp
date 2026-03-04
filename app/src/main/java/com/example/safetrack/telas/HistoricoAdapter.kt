package com.example.safetrack.telas

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safetrack.R
import com.example.safetrack.model.Activity
import com.google.firebase.auth.FirebaseAuth
import java.util.Date

class HistoricoAdapter(
    private val context: Context,
    private val groups: MutableList<HistoryGroup>
) : RecyclerView.Adapter<HistoricoAdapter.GroupViewHolder>() {

    // Adapter auxiliar para os itens de Atividade (simplificado)
    private inner class SimpleActivityAdapter(private val activities: List<Activity>) :
        RecyclerView.Adapter<SimpleActivityAdapter.SimpleActivityViewHolder>() {

        private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        inner class SimpleActivityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.activity_icon)
            val textMain: TextView = view.findViewById(R.id.activity_text_main)
            val textTime: TextView = view.findViewById(R.id.activity_text_time)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleActivityViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.item_atividade,
                parent,
                false
            )
            return SimpleActivityViewHolder(view)
        }

        override fun onBindViewHolder(holder: SimpleActivityViewHolder, position: Int) {
            val activity = activities[position]

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                activity.timestamp,
                Date().time,
                DateUtils.MINUTE_IN_MILLIS
            )
            holder.textTime.text = timeAgo

            // Linha corrigida (sem a formatação ** que causava erro)
            val displayName = if (activity.userId == currentUserId) "Você" else activity.userName

            val local = if (activity.localNome.isNullOrBlank() || activity.localNome == "Localização Indisponível")
                "uma localização"
            else
                activity.localNome

            when (activity.tipo) {
                "chegou em" -> {
                    val color = ContextCompat.getColor(context, R.color.green_500)
                    holder.icon.setImageResource(R.drawable.ic_notifications)
                    holder.icon.setColorFilter(color)
                    holder.textMain.text = "$displayName chegou em $local"
                }
                "saiu de" -> {
                    val color = ContextCompat.getColor(context, R.color.red_500)
                    holder.icon.setImageResource(R.drawable.ic_notifications)
                    holder.icon.setColorFilter(color)
                    holder.textMain.text = "$displayName saiu de $local"
                }
                else -> {
                    val color = ContextCompat.getColor(context, R.color.blue_500)
                    holder.icon.setImageResource(R.drawable.ic_notifications)
                    holder.icon.setColorFilter(color)
                    val actionText = if (local == "uma localização") "está em movimento" else "está em $local"
                    holder.textMain.text = "$displayName $actionText"
                }
            }
        }

        override fun getItemCount() = activities.size
    }

    companion object {
        const val TYPE_DATE = 0
        const val TYPE_MEMBER = 1
    }

    private val expandedPositions = mutableSetOf<Int>()

    data class HistoryGroup(
        val title: String,
        val subTitle: String,
        val items: List<Activity>,
        val type: Int
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_historico_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        val isExpanded = expandedPositions.contains(position)

        holder.txtTitle.text = group.title
        holder.txtSubtitle.text = group.subTitle

        holder.imgArrow.rotation = if (isExpanded) 90f else 0f

        // Configura o RecyclerView interno
        if (isExpanded && group.items.isNotEmpty()) {
            holder.recyclerChild.visibility = View.VISIBLE
            val childAdapter = SimpleActivityAdapter(group.items)

            // O LinearLayoutManager Padrão (false) é usado porque a lista já vem ordenada
            // do mais recente (index 0) para o mais antigo, garantindo a ordem correta.
            holder.recyclerChild.layoutManager = LinearLayoutManager(context)
            holder.recyclerChild.adapter = childAdapter
        } else {
            holder.recyclerChild.visibility = View.GONE
        }

        // Clique para expandir/recolher
        holder.headerLayout.setOnClickListener {
            if (isExpanded) {
                expandedPositions.remove(position)
            } else {
                if (expandedPositions.size >= 2) {
                    val toRemove = expandedPositions.first()
                    expandedPositions.remove(toRemove)
                    notifyItemChanged(toRemove)
                }
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = groups.size

    fun updateData(newGroups: List<HistoryGroup>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerLayout: LinearLayout = view.findViewById(R.id.layout_header)
        val txtTitle: TextView = view.findViewById(R.id.txt_header_title)
        val txtSubtitle: TextView = view.findViewById(R.id.txt_header_subtitle)
        val imgArrow: ImageView = view.findViewById(R.id.img_arrow)
        val recyclerChild: RecyclerView = view.findViewById(R.id.recycler_child)
    }
}