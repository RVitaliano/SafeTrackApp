package com.example.safetrack.telas

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.safetrack.R
import com.example.safetrack.model.Activity
import com.google.firebase.auth.FirebaseAuth
import java.util.Date

class ActivityAdapter(
    // Caminho completo para evitar erro de importação de Context
    private val context: android.content.Context,
    private val activities: List<Activity>
) : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    inner class ActivityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.activity_icon)
        val textMain: TextView = view.findViewById(R.id.activity_text_main)
        val textTime: TextView = view.findViewById(R.id.activity_text_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_atividade,
            parent,
            false
        )
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activities[position]

        val timeAgo = DateUtils.getRelativeTimeSpanString(
            activity.timestamp,
            Date().time,
            DateUtils.MINUTE_IN_MILLIS
        )

        holder.textTime.text = timeAgo

        // Lógica Visual: Se for eu -> "Você". Senão -> Nome do banco.
        val displayName = if (activity.userId == currentUserId) "Você" else activity.userName

        // O localNome precisa ser sanitizado
        val local = if (activity.localNome.isNullOrBlank() || activity.localNome == "Localização Indisponível")
            "uma localização"
        else
            activity.localNome

        when (activity.tipo) {
            "chegou em" -> {
                val color = ContextCompat.getColor(context, R.color.green_500)
                holder.icon.setImageResource(R.drawable.ic_notifications)
                holder.icon.setColorFilter(color)
                holder.textMain.text = "$displayName chegou em $local" // [cite: 146]
            }
            "saiu de" -> {
                val color = ContextCompat.getColor(context, R.color.red_500)
                holder.icon.setImageResource(R.drawable.ic_notifications)
                holder.icon.setColorFilter(color)
                holder.textMain.text = "$displayName saiu de $local" // [cite: 147]
            }
            "está em" -> {
                val color = ContextCompat.getColor(context, R.color.blue_500)
                holder.icon.setImageResource(R.drawable.ic_notifications)
                holder.icon.setColorFilter(color)
                // Se o local for "uma localização" (porque não tem endereço), usamos "está em movimento"
                val actionText = if (local == "uma localização") "está em movimento" else "está em $local"
                holder.textMain.text = "$displayName $actionText"
            }
            else -> {
                val color = ContextCompat.getColor(context, R.color.blue_500)
                holder.icon.setImageResource(R.drawable.ic_notifications)
                holder.icon.setColorFilter(color)
                // Usando o tipo original como fallback
                holder.textMain.text = "$displayName ${activity.tipo} $local" // [cite: 148]
            }
        }
    }

    override fun getItemCount() = activities.size
}