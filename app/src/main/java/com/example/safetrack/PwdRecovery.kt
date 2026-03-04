// Define o pacote do projeto onde essa classe está localizada
package com.example.safetrack


import android.os.Bundle // Importa a classe Bundle, usada para salvar o estado da Activity
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge // Importa a função enableEdgeToEdge, que permite que o layout ocupe a tela inteira, incluindo áreas do sistema (status bar, navigation bar)
import androidx.appcompat.app.AppCompatActivity // Importa a classe AppCompatActivity, base para Activities com compatibilidade com versões antigas do Android
import androidx.core.view.ViewCompat // Importa a classe ViewCompat, que fornece métodos de compatibilidade para Views
import androidx.core.view.WindowInsetsCompat // Importa a classe WindowInsetsCompat, usada para lidar com áreas ocupadas pelo sistema, como barra de status e navegação
import com.example.safetrack.databinding.ActivityPwdRecoveryBinding // Importa o binding da Activity de recuperação de senha (gerado automaticamente pelo Android Studio)


// Define a classe PwdRecovery que herda de AppCompatActivity
class PwdRecovery : AppCompatActivity() {

    private lateinit var onClickListener: () -> Unit

    // Declara uma variável do tipo ActivityPwdRecoveryBinding que será inicializada depois (lateinit)
    private lateinit var binding: ActivityPwdRecoveryBinding

    // Função chamada quando a Activity é criada
    override fun onCreate(savedInstanceState: Bundle?) {
        // Chama o método da superclasse para garantir que a Activity seja configurada corretamente
        super.onCreate(savedInstanceState)
        // Habilita que o layout ocupe toda a tela, passando por cima de barras de sistema
        enableEdgeToEdge()
        // Inicializa o binding usando o layout inflater da Activity
        binding = ActivityPwdRecoveryBinding.inflate(layoutInflater)
        // Define o conteúdo da Activity para ser a raiz do layout inflado pelo binding
        setContentView(binding.root)

        // 1. Obtenha a referência
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

            // 2. Defina o listener de clique
        btnBack.setOnClickListener {
            // Isso fecha a Activity atual e volta para a anterior na pilha
            onBackPressedDispatcher.onBackPressed()
        }
        // Configura um listener para ajustar o padding da view de acordo com as áreas ocupadas pelo sistema (status bar, navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            // Obtém as dimensões das barras do sistema (esquerda, topo, direita, baixo)
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Ajusta o padding da view principal para evitar sobreposição com barras do sistema
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            // Retorna os insets para que possam ser aplicados corretamente
            insets
        }
    }
}
