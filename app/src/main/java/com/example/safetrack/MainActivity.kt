package com.example.safetrack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager // ⚠️ Import necessário
import android.provider.Settings // ⚠️ Import necessário
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog // ⚠️ Import para o AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.safetrack.telas.AtividadeFragment
import com.example.safetrack.telas.ConfigFragment
import com.example.safetrack.telas.FamiliaFragment
import com.example.safetrack.telas.MapaFragment
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var badge: BadgeDrawable? = null
    private var initialActivityCount: Long = 0

    private val activityCounterListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val currentCount = snapshot.childrenCount

            if (initialActivityCount == 0L) {
                initialActivityCount = currentCount
                return
            }

            if (currentCount > initialActivityCount) {
                val newActivities = (currentCount - initialActivityCount).toInt()

                val sharedPrefs = getSharedPreferences("SafeTrackPrefs", Context.MODE_PRIVATE)
                val totalNewCount = sharedPrefs.getInt("notification_count", 0) + newActivities
                sharedPrefs.edit().putInt("notification_count", totalNewCount).apply()

                initialActivityCount = currentCount

                updateBadgeCount(totalNewCount)

                Log.d("ACTIVITY_COUNTER", "Novas Atividades: $newActivities. Total no Badge: $totalNewCount")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("FIREBASE", "Erro ao contar atividades: ${error.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // ⚠️ 1. CHAMADA CRUCIAL PARA A PERSISTÊNCIA DO SERVIÇO
        checkBatteryOptimizations()
        // ----------------------------------------------------

        bottomNav = findViewById(R.id.bottomNav)

        // --- CORREÇÃO DO LAYOUT (EDGE TO EDGE) ---

        // 1. Aplica padding no container PRINCIPAL apenas para TOPO e LATERAIS.
        //    IMPORTANTE: Definimos bottom = 0 aqui para a tela descer até o fim real.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        // 2. Aplica padding APENAS na BottomNav para ela subir acima dos 3 botões do Android
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Aqui usamos updatePadding para somar ao padding original se necessário
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        // ----------------------------------------

        setupActivityListener()

        val savedCount = getSharedPreferences("SafeTrackPrefs", Context.MODE_PRIVATE)
            .getInt("notification_count", 0)
        updateBadgeCount(savedCount)

        if (savedInstanceState == null) {
            loadFragment(MapaFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_mapa -> {
                    loadFragment(MapaFragment())
                    true
                }
                R.id.nav_familia -> {
                    loadFragment(FamiliaFragment())
                    true
                }
                R.id.nav_atividade -> {
                    clearActivityBadge()
                    loadFragment(AtividadeFragment())
                    true
                }
                R.id.nav_config -> {
                    loadFragment(ConfigFragment())
                    true
                }
                else -> false
            }
        }
    }

    // =========================================================================
    //  ⚠️ FUNÇÕES ADICIONADAS PARA PERSISTÊNCIA EM BACKGROUND
    // =========================================================================

    private fun checkBatteryOptimizations() {
        // A otimização de bateria só existe a partir do Android 6 (M)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            // PowerManager é o serviço que gerencia a energia/bateria
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

            // Verifica se o aplicativo NÃO está ignorando as otimizações
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {

                AlertDialog.Builder(this)
                    .setTitle("Rastreamento Contínuo Essencial")
                    .setMessage("Para garantir que o SafeTrack possa rastrear sua localização mesmo com o app fechado (fora da tela de Recentes), ele precisa ser excluído da Otimização de Bateria.")
                    .setPositiveButton("Configurar") { _, _ ->
                        requestIgnoreBatteryOptimizations()
                    }
                    .setNegativeButton("Agora Não", null)
                    .show()
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Cria a Intent que abre a tela de permissão de ignorar otimização
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível abrir as configurações. Procure 'SafeTrack' em 'Otimização de Bateria' nas Configurações do seu celular.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // =========================================================================
    //  FIM DAS FUNÇÕES DE PERSISTÊNCIA
    // =========================================================================

    private fun setupActivityListener() {
        val activitiesRef = FirebaseDatabase.getInstance().getReference("atividades")
        activitiesRef.addValueEventListener(activityCounterListener)
    }

    private fun updateBadgeCount(count: Int) {
        if (count > 0) {
            if (badge == null) {
                badge = bottomNav.getOrCreateBadge(R.id.nav_atividade)
                badge?.backgroundColor = ContextCompat.getColor(this, R.color.red_500) // Assumindo que R.color.red_500 existe
            }
            badge?.isVisible = true
            badge?.number = count
        } else {
            badge?.isVisible = false
            bottomNav.removeBadge(R.id.nav_atividade)
            badge = null
        }
    }

    private fun clearActivityBadge() {
        getSharedPreferences("SafeTrackPrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("notification_count", 0)
            .apply()

        val activitiesRef = FirebaseDatabase.getInstance().getReference("atividades")
        activitiesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                initialActivityCount = snapshot.childrenCount
                updateBadgeCount(0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}