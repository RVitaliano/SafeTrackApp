package com.example.safetrack.telas

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safetrack.R
import com.example.safetrack.utils.GroupManagement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.UUID

private const val NO_GROUP_ID = "sem_grupo"
private const val ACTION_NONE = 0
private const val ACTION_CREATE = 1
private const val ACTION_JOIN = 2

// -------------------------------------------------------------------
// DATA CLASS PARA O GRUPO
// -------------------------------------------------------------------
data class Group(
    val groupId: String = "",
    val name: String = "",
    val password: String = "", // Senha para entrada
    val createdBy: String = ""
)

// -------------------------------------------------------------------
// DATA CLASS MEMBRO
// -------------------------------------------------------------------
data class GroupMember(
    val uid: String,
    val name: String,
    val lastUpdate: Long,
    val isCurrentUser: Boolean
)

// -------------------------------------------------------------------
// ADAPTER
// -------------------------------------------------------------------
class MembersAdapter(private val members: List<GroupMember>) :
    RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageIcon)
        val nameText: TextView = view.findViewById(R.id.textViewEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        val suffix = if (member.isCurrentUser) " (Você)" else ""
        holder.nameText.text = member.name + suffix
    }

    override fun getItemCount() = members.size
}

// -------------------------------------------------------------------
// FRAGMENTO PRINCIPAL
// -------------------------------------------------------------------
class FamiliaFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var locationsRef: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var groupsRef: DatabaseReference

    private var currentUserId: String? = null
    private var currentGroupName: String? = null // Armazena o nome do grupo

    // UI Components
    private lateinit var tvCurrentGroupId: TextView
    private lateinit var tvGroupNameDisplay: TextView // Para exibir o nome em preto

    // Componentes de Ação Inicial
    private lateinit var btnShowCreate: Button
    private lateinit var btnShowJoin: Button
    private lateinit var actionGroupContainer: LinearLayout

    // Componentes dentro do Container Dinâmico
    private lateinit var etGroupName: EditText
    private lateinit var etGroupPassword: EditText
    private lateinit var etNewGroupId: EditText
    private lateinit var btnJoinGroup: Button
    private lateinit var btnCreateGroup: Button
    private lateinit var tvCreateGroupTitle: TextView
    private lateinit var btnCancelAction: Button       // Botão de voltar/cancelar

    // Componentes de visualização de grupo
    private lateinit var btnLeaveGroup: Button
    private lateinit var rvMembers: RecyclerView
    private lateinit var tvTitleMembers: TextView

    private lateinit var membersAdapter: MembersAdapter
    private val membersList = mutableListOf<GroupMember>()

    private var currentGroupId: String = NO_GROUP_ID
    private var currentAction: Int = ACTION_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        locationsRef = database.getReference("localizacoes_usuarios")
        usersRef = database.getReference("users")
        groupsRef = database.getReference("groups")
        currentUserId = auth.currentUser?.uid
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_familia_fragment, container, false)

        // Bind Views
        tvCurrentGroupId = view.findViewById(R.id.textViewCurrentGroupId)
        tvGroupNameDisplay = view.findViewById(R.id.textViewGroupNameDisplay)

        // Binds dos botões de controle de ação inicial
        btnShowCreate = view.findViewById(R.id.btnShowCreate)
        btnShowJoin = view.findViewById(R.id.btnShowJoin)
        actionGroupContainer = view.findViewById(R.id.actionGroupContainer)

        // Binds dos campos dentro do Container
        etGroupName = view.findViewById(R.id.editTextGroupName)
        etGroupPassword = view.findViewById(R.id.editTextGroupPassword)
        etNewGroupId = view.findViewById(R.id.editTextNewGroupId)
        btnJoinGroup = view.findViewById(R.id.buttonUpdateGroupId)
        btnCreateGroup = view.findViewById(R.id.buttonCreateGroup)
        tvCreateGroupTitle = view.findViewById(R.id.tvCreateGroupTitle)
        btnCancelAction = view.findViewById(R.id.btnCancelAction)

        // Binds da visualização de grupo
        btnLeaveGroup = view.findViewById(R.id.buttonLeaveGroup)
        rvMembers = view.findViewById(R.id.recyclerViewMembers)
        tvTitleMembers = view.findViewById(R.id.tvTitleMembers)

        // Configura RecyclerView
        rvMembers.layoutManager = LinearLayoutManager(context)
        membersAdapter = MembersAdapter(membersList)
        rvMembers.adapter = membersAdapter

        // Configura Listeners
        btnShowCreate.setOnClickListener { showActionFields(ACTION_CREATE) }
        btnShowJoin.setOnClickListener { showActionFields(ACTION_JOIN) }
        btnCreateGroup.setOnClickListener { createNewGroup() }
        btnJoinGroup.setOnClickListener { joinExistingGroup() }
        btnCancelAction.setOnClickListener { showInitialButtons() }
        btnLeaveGroup.setOnClickListener { leaveGroup() }

        loadMyGroupId()

        return view
    }

    /**
     * Mostra os campos de texto específicos para Criar ou Entrar, e oculta os botões iniciais.
     */
    private fun showActionFields(action: Int) {
        currentAction = action

        btnShowCreate.visibility = View.GONE
        btnShowJoin.visibility = View.GONE

        actionGroupContainer.visibility = View.VISIBLE
        etGroupName.text.clear()
        etGroupPassword.text.clear()
        etNewGroupId.text.clear()

        when (action) {
            ACTION_CREATE -> {
                tvCreateGroupTitle.text = "Detalhes do Novo Grupo:"
                etGroupName.visibility = View.VISIBLE
                etNewGroupId.visibility = View.GONE
                btnCreateGroup.visibility = View.VISIBLE
                btnJoinGroup.visibility = View.GONE
            }
            ACTION_JOIN -> {
                tvCreateGroupTitle.text = "Detalhes para Entrar:"
                etGroupName.visibility = View.GONE
                etNewGroupId.visibility = View.VISIBLE
                btnCreateGroup.visibility = View.GONE
                btnJoinGroup.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Oculta o container de campos e mostra os botões iniciais de Criar/Entrar.
     */
    private fun showInitialButtons() {
        currentAction = ACTION_NONE
        actionGroupContainer.visibility = View.GONE
        btnShowCreate.visibility = View.VISIBLE
        btnShowJoin.visibility = View.VISIBLE
    }


    private fun createNewGroup() {
        if (currentUserId == null || currentAction != ACTION_CREATE) return

        val name = etGroupName.text.toString().trim()
        val password = etGroupPassword.text.toString().trim()

        if (name.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Preencha o nome e a senha do novo grupo.", Toast.LENGTH_SHORT).show()
            return
        }

        val newRandomCode = UUID.randomUUID().toString().substring(0, 6).uppercase()

        val newGroup = Group(
            groupId = newRandomCode,
            name = name,
            password = password,
            createdBy = currentUserId!!
        )

        groupsRef.child(newRandomCode).setValue(newGroup)
            .addOnSuccessListener {
                performJoinProcess(newRandomCode, isCreating = true)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Falha ao criar o grupo no Firebase.", Toast.LENGTH_LONG).show()
            }
    }

    private fun joinExistingGroup() {
        if (currentUserId == null || currentAction != ACTION_JOIN) return

        val typedGroupId = etNewGroupId.text.toString().trim()
        val typedPassword = etGroupPassword.text.toString().trim()

        if (typedGroupId.isEmpty()) {
            Toast.makeText(context, "Digite o ID do grupo.", Toast.LENGTH_SHORT).show()
            return
        }
        if (typedPassword.isEmpty()) {
            Toast.makeText(context, "Digite a senha do grupo.", Toast.LENGTH_SHORT).show()
            return
        }

        groupsRef.child(typedGroupId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val group = snapshot.getValue(Group::class.java)

                    if (group != null) {
                        if (group.password == typedPassword) {
                            performJoinProcess(typedGroupId, isCreating = false)
                        } else {
                            Toast.makeText(context, "Senha do grupo incorreta!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Grupo com ID '$typedGroupId' não encontrado!", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Erro ao verificar grupo: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }


    private fun performJoinProcess(groupId: String, isCreating: Boolean) {
        if (currentUserId == null) return

        locationsRef.child(currentUserId!!).child("groupId").setValue(groupId)
            .addOnSuccessListener {
                val groupNameForMsg = if (isCreating) etGroupName.text.toString().trim() else groupId
                val msg = if (isCreating) "Grupo '$groupNameForMsg' criado com ID: $groupId" else "Entrou no grupo $groupId"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

                etNewGroupId.text.clear()
                etGroupName.text.clear()
                etGroupPassword.text.clear()
                showInitialButtons()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Erro ao entrar no grupo: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMyGroupId() {
        if (currentUserId == null) return

        locationsRef.child(currentUserId!!).child("groupId")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentGroupId = snapshot.getValue(String::class.java) ?: NO_GROUP_ID
                    currentGroupName = null // Reseta o nome ao carregar

                    if (currentGroupId == NO_GROUP_ID) {
                        tvCurrentGroupId.text = "Você não faz parte de um grupo"
                        toggleGroupUI(hasGroup = false)
                    } else {
                        groupsRef.child(currentGroupId).child("name").get()
                            .addOnSuccessListener { nameSnapshot ->
                                // SALVA O NOME NA VARIÁVEL DA CLASSE
                                currentGroupName = nameSnapshot.getValue(String::class.java)
                                toggleGroupUI(hasGroup = true)
                            }
                            .addOnFailureListener {
                                toggleGroupUI(hasGroup = true)
                            }
                        loadMembersOfGroup(currentGroupId)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Controla a visibilidade dos elementos de UI com base no status do grupo,
     * e formata a exibição do Nome e ID do grupo.
     */
    private fun toggleGroupUI(hasGroup: Boolean) {
        if (hasGroup) {
            // EXIBIÇÃO DE GRUPO
            btnLeaveGroup.visibility = View.VISIBLE
            rvMembers.visibility = View.VISIBLE
            tvTitleMembers.visibility = View.VISIBLE

            // Exibe o Nome em Preto (acima do ID)
            if (!currentGroupName.isNullOrEmpty()) {
                tvGroupNameDisplay.text = "Nome: ${currentGroupName}"
                tvGroupNameDisplay.visibility = View.VISIBLE
                tvCurrentGroupId.text = "ID: $currentGroupId"
            } else {
                tvGroupNameDisplay.visibility = View.GONE
                tvCurrentGroupId.text = "Grupo: $currentGroupId"
            }

            // Oculta tudo relacionado à AÇÃO de Criar/Entrar
            btnShowCreate.visibility = View.GONE
            btnShowJoin.visibility = View.GONE
            actionGroupContainer.visibility = View.GONE

        } else {
            // OPÇÕES DE AÇÃO INICIAL
            btnLeaveGroup.visibility = View.GONE
            rvMembers.visibility = View.GONE
            tvTitleMembers.visibility = View.GONE

            // Oculta o nome, mostra apenas o status padrão
            tvGroupNameDisplay.visibility = View.GONE
            tvCurrentGroupId.text = "Você não faz parte de um grupo"

            // Exibe botões de ação (Criar ou Entrar)
            showInitialButtons()
        }
    }

    private fun loadMembersOfGroup(groupId: String) {
        // ... (lógica de carregamento de membros inalterada) ...
        locationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                membersList.clear()

                if (groupId == NO_GROUP_ID) {
                    membersAdapter.notifyDataSetChanged()
                    return
                }

                val tempList = mutableListOf<GroupMember>()
                var membersProcessed = 0
                var totalMembers = 0

                for (child in snapshot.children) {
                    val gId = child.child("groupId").getValue(String::class.java)
                    if (gId == groupId) {
                        totalMembers++
                    }
                }

                if (totalMembers == 0) {
                    membersAdapter.notifyDataSetChanged()
                    return
                }

                for (userSnapshot in snapshot.children) {
                    val uid = userSnapshot.key ?: continue
                    val userGroup = userSnapshot.child("groupId").getValue(String::class.java)

                    if (userGroup == groupId) {
                        val mapName = userSnapshot.child("name").getValue(String::class.java)
                        val lastUpdate = userSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                        usersRef.child(uid).get().addOnCompleteListener { task ->
                            var finalName = "Carregando..."

                            if (task.isSuccessful && task.result.exists()) {
                                val profileName = task.result.child("name").value as? String
                                if (!profileName.isNullOrEmpty()) {
                                    finalName = profileName
                                } else {
                                    finalName = mapName ?: "Sem Nome"
                                }
                            } else {
                                finalName = mapName ?: "Sem Nome"
                            }

                            val member = GroupMember(
                                uid = uid,
                                name = finalName,
                                lastUpdate = lastUpdate,
                                isCurrentUser = uid == currentUserId
                            )

                            tempList.add(member)
                            membersProcessed++

                            if (membersProcessed == totalMembers) {
                                membersList.clear()
                                membersList.addAll(tempList)
                                membersAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun leaveGroup() {
        if (currentUserId == null) return

        GroupManagement.userLeavesGroupAction {
            Toast.makeText(context, "Você saiu do grupo. Seu histórico de atividades foi apagado.", Toast.LENGTH_LONG).show()
        }
    }
}