package com.example.safetrack

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.appcompat.app.AlertDialog // Importante para o diálogo de confirmação

class EditProfileActivity : AppCompatActivity() {

    private lateinit var edtName: EditText
    private lateinit var edtPhone: EditText
    private lateinit var edtPasswordConfirm: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageView
    private lateinit var btnDeleteAccount: Button // NOVO: Botão de exclusão

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private var originalName: String = ""
    private var originalPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        edtName = findViewById(R.id.edtName)
        edtPhone = findViewById(R.id.edtPhone)
        edtPasswordConfirm = findViewById(R.id.edtPasswordConfirm)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        // Filtros
        edtName.filters = arrayOf(InputFilter.LengthFilter(24))
        edtPhone.filters = arrayOf(InputFilter.LengthFilter(11))

        loadUserData()

        btnSave.setOnClickListener { validateAndProcessSave() }
        btnBack.setOnClickListener { finish() }

        // NOVO: Adiciona o listener para o botão de exclusão
        btnDeleteAccount.setOnClickListener { confirmAndDeleteAccount() }
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        Toast.makeText(this, "Carregando informações...", Toast.LENGTH_SHORT).show()

        database.child("localizacoes_usuarios").child(userId).get()
            .addOnSuccessListener { snapshot ->
                val phoneLoc = snapshot.child("phone").value?.toString()
                val nameLoc = snapshot.child("name").value?.toString()

                if (!phoneLoc.isNullOrEmpty()) {
                    edtPhone.setText(phoneLoc)
                    originalPhone = phoneLoc
                }

                if (!nameLoc.isNullOrEmpty()) {
                    edtName.setText(nameLoc)
                    originalName = nameLoc
                } else {
                    val authName = user.displayName
                    if (!authName.isNullOrEmpty()) {
                        edtName.setText(authName)
                        originalName = authName
                    }
                }
                // Habilita botões após carregar
                btnSave.isEnabled = true
                btnDeleteAccount.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao carregar dados.", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnDeleteAccount.isEnabled = true
            }
    }

    // --- LÓGICA DE EXCLUSÃO DE CONTA ---

    private fun confirmAndDeleteAccount() {
        // 1. Verifica se a senha foi digitada
        val password = edtPasswordConfirm.text.toString()
        if (password.isEmpty()) {
            edtPasswordConfirm.error = "A senha é obrigatória para excluir a conta."
            Toast.makeText(this, "Digite sua senha para confirmar.", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Diálogo de Confirmação Final
        AlertDialog.Builder(this)
            .setTitle("Confirmar Exclusão de Conta")
            .setMessage("Tem certeza que deseja apagar sua conta? Todos os seus dados serão PERDIDOS permanentemente.")
            .setPositiveButton("APAGAR") { _, _ ->
                deleteAccount(password)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteAccount(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val userId = user.uid

        btnSave.isEnabled = false
        btnDeleteAccount.isEnabled = false
        Toast.makeText(this, "Iniciando exclusão de dados...", Toast.LENGTH_SHORT).show()

        val credential = EmailAuthProvider.getCredential(email, password)

        // Passo 1: Reautenticar o usuário antes de apagar a conta
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Passo 2: Remover dados do Realtime Database
                performDatabaseDeletion(userId)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Senha incorreta!", Toast.LENGTH_LONG).show()
                edtPasswordConfirm.error = "Senha incorreta"
                btnSave.isEnabled = true
                btnDeleteAccount.isEnabled = true
            }
    }

    private fun performDatabaseDeletion(userId: String) {
        // Apagar todos os dados do usuário, exceto os grupos que ele possa ter criado,
        // mas aqui focamos nos dados diretos dele.

        // Exemplo de caminhos de dados que você pode querer apagar:
        val pathsToDelete = listOf(
            "localizacoes_usuarios/$userId",
            "atividades/$userId",
            // Se houverem outras tabelas: "outra_tabela/$userId"
        )

        val deletionTasks = pathsToDelete.map { path ->
            database.child(path).removeValue()
        }

        // Simplesmente apagamos o nó principal do usuário em localizacoes_usuarios para este exemplo
        database.child("localizacoes_usuarios").child(userId).removeValue()
            .addOnSuccessListener {
                // Passo 3: Apagar a conta do Firebase Auth
                deleteUserFromAuth()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao apagar dados: ${e.message}", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = true
                btnDeleteAccount.isEnabled = true
            }
    }

    private fun deleteUserFromAuth() {
        val user = auth.currentUser ?: return

        user.delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Conta apagada com sucesso!", Toast.LENGTH_LONG).show()

                // Redireciona para a tela de Login ou de Início
                // (Substitua LoginActivity::class.java pela sua tela inicial/login)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao apagar Auth: ${e.message}", Toast.LENGTH_LONG).show()
                // Se a exclusão do Auth falhar, os dados do Realtime Database já foram removidos,
                // mas a conta no Auth ainda existe.
                btnSave.isEnabled = true
                btnDeleteAccount.isEnabled = true
            }
    }

    // --- LÓGICA DE SALVAMENTO DE PERFIL ---

    private fun validateAndProcessSave() {
        val name = edtName.text.toString().trim()
        val phone = edtPhone.text.toString().trim()
        val password = edtPasswordConfirm.text.toString()

        if (name == originalName && phone == originalPhone) {
            Toast.makeText(this, "Nenhuma alteração detectada para salvar.", Toast.LENGTH_SHORT).show()
            return
        }


        if (!name.matches(Regex("^[A-Za-zÀ-ÿ ]+$"))) {
            edtName.error = "Use apenas letras"; return}

        if (name.isEmpty()) { edtName.error = "Nome obrigatório"; return }
        if (name.length < 3) {edtName.error = "Nome muito pequeno"; return}
        if (name.length > 24) { edtName.error = "Máx 24 caracteres"; return }

        if (phone.isEmpty()) { edtPhone.error = "Telefone obrigatório"; return }
        if (phone.length != 11) { edtPhone.error = "Deve ter 11 dígitos (DDD+9)"; return }

        // Senha só é necessária para exclusão ou alteração de dados sensíveis como e-mail/senha.
        // Como aqui só estamos alterando nome/telefone (não sensível para o Auth), a senha é opcional
        // se não houver lógica de reautenticação obrigatória. Mas como você a usou para autenticar antes,
        // vamos mantê-la como obrigatória para evitar quebra de fluxo.
        if (password.isEmpty()) { edtPasswordConfirm.error = "Senha necessária para salvar"; return }


        btnSave.isEnabled = false

        if (phone != originalPhone) {
            checkUniquePhoneAndSave(name, phone, password)
        } else {
            // Reautentica apenas para garantir que o usuário é o dono, mesmo que não mude o telefone.
            authenticateAndSave(name, phone, password)
        }
    }

    private fun checkUniquePhoneAndSave(name: String, phone: String, password: String) {
        database.child("localizacoes_usuarios")
            .orderByChild("phone")
            .equalTo(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        edtPhone.error = "Este telefone já está em uso."
                        Toast.makeText(baseContext, "Telefone indisponível.", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                    } else {
                        authenticateAndSave(name, phone, password)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(baseContext, "Erro na verificação.", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                }
            })
    }

    private fun authenticateAndSave(name: String, phone: String, password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        Toast.makeText(this, "Verificando senha...", Toast.LENGTH_SHORT).show()

        val credential = EmailAuthProvider.getCredential(email, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                performDatabaseUpdate(user.uid, name, phone)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Senha incorreta!", Toast.LENGTH_LONG).show()
                edtPasswordConfirm.error = "Senha incorreta"
                btnSave.isEnabled = true
            }
    }

    private fun performDatabaseUpdate(userId: String, name: String, phone: String) {
        val userData = mapOf<String, Any>(
            "name" to name,
            "phone" to phone
        )

        database.child("localizacoes_usuarios").child(userId).updateChildren(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Perfil atualizado com sucesso!", Toast.LENGTH_LONG).show()

                originalName = name
                originalPhone = phone
                edtPasswordConfirm.text.clear()
                btnSave.isEnabled = true

                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
            }
    }
}