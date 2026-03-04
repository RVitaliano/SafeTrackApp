package com.example.safetrack.telas

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safetrack.R
import com.example.safetrack.model.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AtividadeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var activityAdapter: ActivityAdapter

    private val displayList = mutableListOf<Activity>()
    private val memberActivitiesMap = mutableMapOf<String, List<Activity>>()

    private val db = FirebaseDatabase.getInstance()
    private val activitiesRef = db.getReference("atividades")
    private val usersRef = db.getReference("localizacoes_usuarios")

    private val activeQueries = mutableListOf<Query>()
    private val activeListeners = mutableListOf<ValueEventListener>()

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_atividade_fragment, container, false)

        val btnHistory = view.findViewById<ImageView>(R.id.icon_notification)
        btnHistory.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HistoricoFragment())
                .addToBackStack(null)
                .commit()
        }

        recyclerView = view.findViewById(R.id.recycler_view_atividades)
        recyclerView.layoutManager = LinearLayoutManager(context)

        activityAdapter = ActivityAdapter(requireContext(), displayList)
        recyclerView.adapter = activityAdapter

        if (currentUserId != null) {
            identifyGroupAndStartListeners()
        }

        return view
    }

    private fun identifyGroupAndStartListeners() {
        if (currentUserId == null) return

        usersRef.child(currentUserId).child("groupId").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val myGroupId = snapshot.getValue(String::class.java)

                if (myGroupId == null || myGroupId == "sem_grupo") {
                    setupListenersForMembers(listOf(currentUserId))
                } else {
                    findGroupMembers(myGroupId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun findGroupMembers(groupId: String) {
        usersRef.orderByChild("groupId").equalTo(groupId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val memberIds = mutableListOf<String>()
                for (child in snapshot.children) {
                    child.key?.let { memberIds.add(it) }
                }
                setupListenersForMembers(memberIds)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Leitura Cirúrgica por Subpasta
    private fun setupListenersForMembers(memberIds: List<String>) {
        clearListeners()
        memberActivitiesMap.clear()

        for (memberId in memberIds) {
            // Vai direto na pasta do usuário: /atividades/ID
            val query = activitiesRef.child(memberId).limitToLast(2)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activities = snapshot.children.mapNotNull { it.getValue(Activity::class.java) }

                    memberActivitiesMap[memberId] = activities
                    refreshDisplayList()
                }
                override fun onCancelled(error: DatabaseError) {}
            }

            query.addValueEventListener(listener)
            activeQueries.add(query)
            activeListeners.add(listener)
        }
    }

    private fun refreshDisplayList() {
        val allActivities = mutableListOf<Activity>()

        for ((_, acts) in memberActivitiesMap) {
            allActivities.addAll(acts)
        }

        allActivities.sortByDescending { it.timestamp }

        displayList.clear()
        displayList.addAll(allActivities)

        if (isAdded) {
            activityAdapter.notifyDataSetChanged()
        }
    }

    private fun clearListeners() {
        for (i in activeQueries.indices) {
            activeQueries[i].removeEventListener(activeListeners[i])
        }
        activeQueries.clear()
        activeListeners.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearListeners()
    }
}