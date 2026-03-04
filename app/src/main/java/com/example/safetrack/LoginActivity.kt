package com.example.safetrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safetrack.databinding.ActivityLoginBinding

// FIREBASE AUTH
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.ktx.Firebase

// FIREBASE DATABASE
import com.google.firebase.database.FirebaseDatabase

// GOOGLE SIGN-IN
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    // GOOGLE SIGN-IN
    private val RC_SIGN_IN = 9001
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializa Auth
        auth = FirebaseAuth.getInstance()

        // Configuração do Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setUpView()

        val pwdRecovery = findViewById<TextView>(R.id.pwdrecovery)
        pwdRecovery.setOnClickListener {
            val intent = Intent(this, PwdRecovery::class.java)
            startActivity(intent)
        }
    }

    private fun setUpView() {
        binding.btnLogin.setOnClickListener {
            realizarLoginComFirebase()
        }

        binding.googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        binding.singUp.setOnClickListener {
            val intent = Intent(this@LoginActivity, SingUpActivity::class.java)
            startActivity(intent)
        }
    }

    // --- LOGIN PADRÃO (EMAIL/SENHA) ---
    private fun realizarLoginComFirebase() {
        val email = binding.edtemail.text.toString().trim()
        val senha = binding.edtPwd.text.toString().trim()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() || senha.isEmpty()) {
            if (email.isEmpty()) binding.edtemail.error = "E-mail obrigatório"
            if (senha.isEmpty()) binding.edtPwd.error = "Senha obrigatória"
            return
        }

        auth.signInWithEmailAndPassword(email, senha)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "Login bem-sucedido!", Toast.LENGTH_SHORT).show()
                    irParaMain()
                } else {
                    Toast.makeText(baseContext, "Falha na autenticação.", Toast.LENGTH_LONG).show()
                }
            }
    }

    // --- LOGIN GOOGLE ---
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("LoginActivity", "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("LoginActivity", "Google sign in failed", e)
                Toast.makeText(this, "Falha no Login com Google.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // AQUI ESTÁ A CORREÇÃO: Salvar na tabela que você mostrou na imagem
                        salvarDadosUsuarioGoogle(user)
                    } else {
                        irParaMain()
                    }
                } else {
                    Toast.makeText(this, "Autenticação Firebase falhou com Google.", Toast.LENGTH_LONG).show()
                }
            }
    }

    // --- FUNÇÃO QUE SALVA NO DATABASE (localizacoes_usuarios) ---
    private fun salvarDadosUsuarioGoogle(user: FirebaseUser) {
        val database = FirebaseDatabase.getInstance()
        // Aponta para a tabela da sua imagem: localizacoes_usuarios > UID
        val myRef = database.getReference("localizacoes_usuarios").child(user.uid)

        // Prepara os dados.
        // Se o nome vier vazio do Google, coloca um padrão.
        val userData = hashMapOf<String, Any>(
            "name" to (user.displayName ?: "Usuário Google"),
            "email" to (user.email ?: "")
            // Não colocamos groupId aqui para não sobrescrever caso o usuário já tenha grupo
        )

        // updateChildren é vital: Atualiza ou Cria o nome/email SEM APAGAR o restante (latitude, longitude, groupId)
        myRef.updateChildren(userData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Bem-vindo, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                }
                // Independente de salvar ou dar erro de rede, deixamos o usuário entrar
                irParaMain()
            }
    }

    private fun irParaMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            irParaMain()
        }
    }
}