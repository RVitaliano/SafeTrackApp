package com.example.safetrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safetrack.databinding.ActivitySingUpBinding // Mantemos só para o setContentView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

class SingUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySingUpBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySingUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth

        // Configura o botão usando o ID direto, sem confiar no binding para o click
        findViewById<ImageButton>(R.id.btnBack2).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSignUp).setOnClickListener {
            validateAndRegisterManual()
        }
    }

    private fun validateAndRegisterManual() {
        // --- MÉTODO MANUAL (SEM BINDING) ---
        // Isso garante que estamos pegando o campo exato pelo ID do XML novo
        val edtNome = findViewById<EditText>(R.id.edtNomeCorreto)
        val edtEmail = findViewById<EditText>(R.id.edtEmailCorreto)
        val edtPhone = findViewById<EditText>(R.id.edtPhone)
        val edtPwd = findViewById<EditText>(R.id.edtPwd)
        val edtConfirm = findViewById<EditText>(R.id.confirm_edtPwd)

        val nomeTexto = edtNome.text.toString().trim()
        val emailTexto = edtEmail.text.toString().trim()
        val phoneTexto = edtPhone.text.toString().trim()
        val pwdTexto = edtPwd.text.toString()
        val confirmTexto = edtConfirm.text.toString()
        val regexNome = "^[A-Za-zÀ-ÖØ-öø-ÿ ]+$".toRegex()

        // --- DEBUG NO LOGCAT ---
        // Olhe a aba Logcat no Android Studio. Vai aparecer isso aqui:
        Log.e("TESTE_REAL", "O QUE FOI DIGITADO NO NOME: '$nomeTexto'")
        Log.e("TESTE_REAL", "O QUE FOI DIGITADO NO EMAIL: '$emailTexto'")

        // TRAVA: Se o nome tiver @, para tudo.
        if (nomeTexto.contains("@")) {
            Toast.makeText(this, "PARE! Você digitou e-mail no campo Nome!", Toast.LENGTH_LONG).show()
            edtNome.error = "Aqui é nome, sem @ pai"
            return
        }

        if (nomeTexto.isEmpty()) {
            edtNome.error = "Nome vazio"
            return
        }
        if (phoneTexto.length != 11) {
            edtPhone.error = "Telefone sao 11 dígitos"
            return
        }
        if (emailTexto.isEmpty()) {
            edtEmail.error = "Email vazio"
            return
        }
        if (pwdTexto.length < 8) {
            edtPwd.error = "Senha curta"
            return
        }
        if (pwdTexto != confirmTexto) {
            edtConfirm.error = "Senhas diferentes"
            return
        }

        if (!nomeTexto.matches(regexNome)) {
            edtNome.error = "Digite apenas letras"
            return
        }



        // Se passou, chama o cadastro
        Log.e("VERIFICACAO", "Texto que está na variável nomeTexto: $nomeTexto")
        Log.e("VERIFICACAO", "Texto que está na variável emailTexto: $emailTexto")
        createAuthAccount(nomeTexto, phoneTexto, emailTexto, pwdTexto)
    }

    // ------------------------------------------------------------------------
    // SUBSTITUA DAQUI PARA BAIXO (AS 3 FUNÇÕES DE LÓGICA)
    // ------------------------------------------------------------------------

    private fun createAuthAccount(nome: String, telefone: String, email: String, pwd: String) {
        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(nome)
                        .build()

                    user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                        Log.d("CADASTRO", "Auth atualizado com Nome: $nome")

                        // ATENÇÃO AQUI: A ordem tem que ser rigorosamente a mesma da função de baixo
                        checkPhoneAndSave(nome, telefone, email)
                    }
                } else {
                    Toast.makeText(baseContext, "Erro Auth: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Recebe: NOME, TELEFONE, EMAIL
    private fun checkPhoneAndSave(nome: String, telefone: String, email: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("localizacoes_usuarios")
        val currentUser = auth.currentUser

        dbRef.orderByChild("phone").equalTo(telefone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Se o telefone já existe, apaga o user do Auth para não ficar dados órfãos
                        currentUser?.delete()
                        Toast.makeText(baseContext, "Este telefone já possui conta!", Toast.LENGTH_LONG).show()
                    } else {
                        if (currentUser != null) {
                            // ATENÇÃO AQUI: Passando as variáveis na ordem correta
                            saveUserToDatabase(currentUser.uid, nome, telefone, email)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    currentUser?.delete()
                }
            })
    }

    // Recebe: UID, NOME, TELEFONE, EMAIL
    private fun saveUserToDatabase(uid: String, nome: String, telefone: String, email: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("localizacoes_usuarios").child(uid)

        val userData = hashMapOf<String, Any>()
        userData["name"] = nome      // Garante que a chave 'name' recebe a variável 'nome'
        userData["email"] = email
        userData["phone"] = telefone
        userData["groupId"] = "sem_grupo"
        userData["latitude"] = 0.0
        userData["longitude"] = 0.0
        userData["timestamp"] = System.currentTimeMillis()

        userRef.setValue(userData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(baseContext, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                goToMainActivity()
            } else {
                Toast.makeText(baseContext, "Erro ao salvar no banco: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}