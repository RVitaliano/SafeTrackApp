package com.example.safetrack.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Classe utilitária para gerenciar ações relacionadas a grupos e limpeza de dados.
 */
object GroupManagement {

    private val db = FirebaseDatabase.getInstance()
    private const val TAG = "GroupManagement"
    private const val NO_GROUP_ID = "sem_grupo"

    /**
     * Inicia o processo de limpar o histórico e resetar o grupo, obtendo primeiro o groupId antigo.
     */
    private fun getGroupIdAndClearData(userId: String, onSuccess: () -> Unit) {
        db.getReference("localizacoes_usuarios").child(userId).child("groupId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val oldGroupId = snapshot.getValue(String::class.java) ?: NO_GROUP_ID

                    // Continua com o processo de limpeza, passando o groupId antigo
                    clearUserDataOnGroupExit(userId, oldGroupId, onSuccess)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Falha ao obter groupId para exclusão: $userId", error.toException())
                    // Tenta limpar mesmo sem o ID
                    clearUserDataOnGroupExit(userId, NO_GROUP_ID, onSuccess)
                }
            })
    }

    /**
     * Limpa o histórico de atividades (apagando-o do banco de dados) e reseta
     * os dados de localização/grupo do usuário quando ele sai do grupo.
     *
     * @param userId O ID do usuário que está saindo.
     * @param oldGroupId O ID do grupo que o usuário está deixando.
     * @param onSuccess Callback executado após o sucesso da limpeza de dados de localização.
     */
    fun clearUserDataOnGroupExit(userId: String, oldGroupId: String, onSuccess: () -> Unit = {}) {

        // 1. Ação PRINCIPAL: Apagar TODO o Histórico de Atividades: /atividades/{userId}
        // *********************************************************************************
        val activitiesRef = db.getReference("atividades").child(userId)
        activitiesRef.removeValue()
            .addOnSuccessListener {
                // SUCESSO na exclusão física. Agora resetamos o groupId e verificamos a exclusão do grupo.
                Log.d(TAG, "SUCESSO: Histórico de atividades apagado (nó removido) para o usuário: $userId")
                resetLocationAndGroupData(userId, oldGroupId, onSuccess)
            }
            .addOnFailureListener { e ->
                // Falha na exclusão. Tentamos resetar o groupId mesmo assim.
                Log.e(TAG, "FALHA: Não foi possível apagar histórico de atividades. Tentando resetar dados de localização: $userId", e)
                resetLocationAndGroupData(userId, oldGroupId, onSuccess)
            }

        // 2. Apagar Pontos de Interesse (POIs) criados pelo usuário (opcional)
        // *********************************************************************************
        val poisRef = db.getReference("pontos_interesse")
        poisRef.orderByChild("createdBy").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    child.ref.removeValue()
                }
                Log.d(TAG, "Pontos de Interesse criados por $userId foram apagados.")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Falha ao buscar POIs para exclusão: $userId", error.toException())
            }
        })
    }

    /**
     * Reseta os campos de localização diários e define o groupId como 'sem_grupo'.
     * Após o reset, verifica se o grupo deve ser excluído.
     */
    private fun resetLocationAndGroupData(userId: String, oldGroupId: String, onSuccess: () -> Unit) {
        val userLocationRef = db.getReference("localizacoes_usuarios").child(userId)

        // Limpa campos essenciais ao sair do grupo
        val updates = mapOf<String, Any?>(
            "groupId" to NO_GROUP_ID, // Define o status como sem grupo
            "dailyMeters" to 0,       // Zera o contador de caminhada
            "lastCalcLat" to null,    // Limpa a última localização de cálculo
            "lastCalcLng" to null,    // Limpa a última localização de cálculo
            "lastNotifiedAddress" to null // Limpa o controle de notificação de endereço
        )

        userLocationRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "SUCESSO: Dados de grupo e diários resetados para o usuário: $userId")

                // NOVO: Verifica e exclui o grupo se o usuário que saiu era o último.
                checkAndDeleteGroupIfEmpty(oldGroupId)

                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "FALHA: Ao resetar dados de grupo/localização: $userId", e)
            }
    }

    /**
     * Verifica se o grupo ficou sem membros e o exclui do nó /groups.
     */
    fun checkAndDeleteGroupIfEmpty(groupId: String) {
        if (groupId == NO_GROUP_ID) return

        val locationsRef = db.getReference("localizacoes_usuarios")

        locationsRef.orderByChild("groupId").equalTo(groupId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Se o snapshot estiver vazio, significa que não há mais usuários com este groupId
                    if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                        val groupsRef = db.getReference("groups")
                        groupsRef.child(groupId).removeValue()
                            .addOnSuccessListener {
                                Log.d(TAG, "SUCESSO: Grupo $groupId excluído pois ficou vazio.")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "FALHA: Não foi possível excluir o grupo vazio $groupId", e)
                            }
                    } else {
                        Log.d(TAG, "Grupo $groupId ainda tem ${snapshot.childrenCount} membro(s). Não será excluído.")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Erro ao verificar membros do grupo $groupId", error.toException())
                }
            })
    }

    /**
     * Função pública que deve ser chamada pela sua interface (ex: um botão no SettingsFragment).
     */
    fun userLeavesGroupAction(onSuccess: () -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId != null) {
            Log.d(TAG, "Iniciando processo de saída do grupo para UID: $currentUserId")
            getGroupIdAndClearData(currentUserId, onSuccess)
        } else {
            Log.e(TAG, "Erro: Usuário não logado, impossível sair do grupo.")
        }
    }
}