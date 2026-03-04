package com.example.safetrack.telas

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager // Necessário para buscar a versão do app
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.safetrack.LoginActivity
import com.example.safetrack.R
import com.example.safetrack.EditProfileActivity
import com.google.firebase.auth.FirebaseAuth

class ConfigFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla o layout
        val view = inflater.inflate(R.layout.activity_config_fragment, container, false)

        // 1. Configura Botão de Logout
        val logoutView: View = view.findViewById(R.id.btnlogout)
        logoutView.setOnClickListener {
            logoutUser()
        }

        // 2. Configura Botão de Gerenciar Conta
        val editProfileBtn: View = view.findViewById(R.id.btnEditProfile)
        editProfileBtn.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            startActivity(intent)
        }

        // 3. Configura Botão "Sobre o SafeTrack"
        val aboutBtn: View = view.findViewById(R.id.btnAboutSafetrack)
        aboutBtn.setOnClickListener {
            showAboutPopup()
        }

        return view
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    // --- FUNÇÃO DO POPUP "SOBRE O SAFETRACK" CORRIGIDA ---
    private fun showAboutPopup() {
        val context = requireContext()
        // Infla o layout customizado
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about_safetrack, null)

        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 1. Busca os TextViews para atualização
        val txtVersion = dialogView.findViewById<TextView>(R.id.txtVersion)
        val txtDeveloper = dialogView.findViewById<TextView>(R.id.txtDeveloper)
        val linkPrivacy = dialogView.findViewById<TextView>(R.id.linkPrivacy)
        val linkTerms = dialogView.findViewById<TextView>(R.id.linkTerms)

        // 2. Lógica para buscar a versão do aplicativo
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.versionCode

            // 3. Atualiza os textos no popup
            txtVersion.text = "Versão: $versionName (Build: $versionCode)"
            txtDeveloper.text = "Desenvolvido por: Absolut5"

        } catch (e: PackageManager.NameNotFoundException) {
            txtVersion.text = "Versão: (Erro ao carregar)"
            txtDeveloper.text = "Desenvolvido por: Absolut5"
        }

        // Função auxiliar para abrir links
        fun openLink(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Ações de clique nos links
        linkPrivacy.setOnClickListener {
            openLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ&start_radio=1")
        }

        linkTerms.setOnClickListener {
            openLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ&start_radio=1")
        }

        dialog.show()
    }
}