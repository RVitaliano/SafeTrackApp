package com.example.safetrack.telas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safetrack.R
import com.example.safetrack.model.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HistoricoFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoricoAdapter
    private val db = FirebaseDatabase.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private val activitiesRef = db.getReference("atividades")
    private val usersRef = db.getReference("localizacoes_usuarios")
    private val memberNames = mutableMapOf<String, String>()
    private val memberMeters = mutableMapOf<String, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_historico, container, false)

        val btnBack = view.findViewById<ImageView>(R.id.btn_back)
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val title = view.findViewById<TextView>(R.id.historico_title)
        title.text = "Histórico de Localização"

        recyclerView = view.findViewById(R.id.recycler_historico)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = HistoricoAdapter(requireContext(), mutableListOf())
        recyclerView.adapter = adapter

        if (currentUser != null) {
            checkGroupStatusAndLoad()
        }

        return view
    }

    private fun checkGroupStatusAndLoad() {
        val uid = currentUser?.uid ?: return

        usersRef.child(uid).child("groupId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val groupId = snapshot.getValue(String::class.java)

                    if (groupId == null || groupId == "sem_grupo") {
                        loadDataNoGroup(uid)
                    } else {
                        loadDataWithGroup(groupId)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

// ========================================================================
// MODO SEM GRUPO (Agrupado por Data)
// ========================================================================

    private fun loadDataNoGroup(uid: String) {
        // Busca a localização do usuário atual para obter os metros diários
        usersRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val todayMeters = snapshot.child("dailyMeters").getValue(Int::class.java) ?: 0

                // Busca as atividades do usuário atual
                activitiesRef.child(uid).orderByChild("timestamp")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(activitiesSnapshot: DataSnapshot) {
                            val allActivities =
                                activitiesSnapshot.children.mapNotNull { it.getValue(Activity::class.java) }
                                    .sortedByDescending { it.timestamp } // Ordena TODAS as atividades (mais recente primeiro)

                            val grouped = allActivities.groupBy { formatDate(it.timestamp) }
                            val historyItems = mutableListOf<HistoricoAdapter.HistoryGroup>()

                            // CORREÇÃO: Ordena os GRUPOS por data (mais recente no topo)
                            grouped.entries
                                // Ordena as entradas do Map pelo timestamp da PRIMEIRA atividade do grupo (que é a mais recente)
                                .sortedByDescending { it.value.first().timestamp }
                                .forEach { (date, acts) ->

                                    val limitedActs =
                                        acts.take(10) // Limita a 10 atividades por dia
                                    val isToday = isToday(acts.first().timestamp)

                                    historyItems.add(
                                        HistoricoAdapter.HistoryGroup(
                                            title = if (isToday) "Você (Hoje)" else date,
                                            subTitle = "Caminhou hoje por: ${todayMeters}M",
                                            items = limitedActs.toList(), // Lista já ordenada do mais recente ao mais antigo
                                            type = HistoricoAdapter.TYPE_DATE
                                        )
                                    )
                                }

                            adapter.updateData(historyItems)
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

// ========================================================================
// MODO COM GRUPO (Agrupado por Membro)
// ========================================================================

    private fun loadDataWithGroup(groupId: String) {
        // 1. Busca todos os membros do grupo e seus metros diários
        usersRef.orderByChild("groupId").equalTo(groupId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val memberIds = mutableListOf<String>()

                    for (user in snapshot.children) {
                        val uid = user.key ?: continue
                        memberIds.add(uid)

                        val name = user.child("name").getValue(String::class.java)
                            ?: user.child("email").getValue(String::class.java)
                            ?: "Desconhecido"

                        memberNames[uid] = name
                        memberMeters[uid] = user.child("dailyMeters").getValue(Int::class.java) ?: 0
                    }

                    if (memberIds.isEmpty()) {
                        adapter.updateData(emptyList())
                        return
                    }

                    // 2. Busca as atividades para cada membro
                    val historyItems = mutableListOf<HistoricoAdapter.HistoryGroup>()
                    var processed = 0
                    val total = memberIds.size

                    memberIds.forEach { uid ->
                        activitiesRef.child(uid).orderByChild("timestamp")
                            .limitToLast(10) // Limite de 10 atividades
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(activitiesSnapshot: DataSnapshot) {
                                    val acts = activitiesSnapshot.children.mapNotNull {
                                        it.getValue(Activity::class.java)
                                    }
                                        .sortedByDescending { it.timestamp } // Garante a ordenação do mais recente para o mais antigo

                                    if (acts.isNotEmpty()) {
                                        historyItems.add(
                                            HistoricoAdapter.HistoryGroup(
                                                title = if (uid == currentUser?.uid) "Você" else (memberNames[uid]
                                                    ?: "Desconhecido"),
                                                subTitle = "Caminhou hoje por: ${memberMeters[uid] ?: 0}M",
                                                items = acts.toList(),
                                                type = HistoricoAdapter.TYPE_MEMBER
                                            )
                                        )
                                    }
                                    processed++
                                    if (processed == total) {
                                        // 3. Ordena os itens (Você primeiro, depois em ordem alfabética)
                                        historyItems.sortWith(Comparator { a, b ->
                                            when {
                                                a.title == "Você" -> -1
                                                b.title == "Você" -> 1
                                                else -> a.title.compareTo(
                                                    b.title,
                                                    ignoreCase = true
                                                )
                                            }
                                        })
                                        adapter.updateData(historyItems)
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    processed++
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

// ========================================================================
// FUNÇÕES DE UTILIDADE
// ========================================================================

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun isToday(timestamp: Long): Boolean {
        val calActivity = Calendar.getInstance().apply { timeInMillis = timestamp }
        val calToday = Calendar.getInstance()

        return calActivity.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
                calActivity.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
    }
}