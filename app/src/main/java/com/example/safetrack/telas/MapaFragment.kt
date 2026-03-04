package com.example.safetrack.telas

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safetrack.R
import com.example.safetrack.LocationTrackingService
import com.example.safetrack.model.Activity
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Calendar

// ============================================================================
// MODELOS DE DADOS
// ============================================================================

data class PointOfInterest(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val groupId: String = "",
    val createdBy: String = ""
)

data class MemberLocation(
    val uid: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val isCurrentUser: Boolean
)

// Constantes
private const val LOCATION_PERMISSION_REQUEST_CODE = 1
private const val NO_GROUP_ID = "sem_grupo"
private const val MIN_DISTANCE_FOR_ACTIVITY_M = 15.0
private const val DOUBLE_CLICK_TIME_DELTA = 500L

// ============================================================================
// ADAPTER DA TOOLBAR (VISUAL STORIES/BOLINHAS)
// ============================================================================
class ToolbarMembersAdapter(
    private val members: List<MemberLocation>,
    private val onMemberClickListener: (Double, Double) -> Unit
) : RecyclerView.Adapter<ToolbarMembersAdapter.MemberToolbarViewHolder>() {

    class MemberToolbarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.memberName)
        val container: View = view.findViewById(R.id.member_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberToolbarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member_toolbar, parent, false)
        return MemberToolbarViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberToolbarViewHolder, position: Int) {
        val member = members[position]

        val displayName = if (member.isCurrentUser) "Você" else member.name.split(" ").firstOrNull() ?: "Membro"
        val finalDisplay = if (displayName.length > 9) displayName.substring(0, 9) + "." else displayName

        holder.nameText.text = finalDisplay
        holder.container.setOnClickListener { onMemberClickListener(member.lat, member.lng) }
    }

    override fun getItemCount() = members.size
}

// ============================================================================
// FRAGMENTO PRINCIPAL DO MAPA
// ============================================================================
class MapaFragment : Fragment(), OnMapReadyCallback {

    private lateinit var recyclerViewGroupMembers: RecyclerView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var locationsRef: DatabaseReference
    private lateinit var activitiesRef: DatabaseReference
    private lateinit var poisRef: DatabaseReference

    private var activityValueListener: ValueEventListener? = null
    private var otherUsersValueListener: ValueEventListener? = null
    private var poisValueListener: ValueEventListener? = null

    private var map: GoogleMap? = null
    private val otherUserMarkers = mutableMapOf<String, Marker>()
    private val poiMarkers = mutableListOf<Marker>()
    private var myLocationMarker: Marker? = null
    private var firstZoomDone = false
    private var currentUserGroupId: String? = null
    private var lastCalculationLocation: Location? = null

    private lateinit var toolbarMembersAdapter: ToolbarMembersAdapter
    private val groupMembersList = mutableListOf<MemberLocation>()

    private var tempCreationMarker: Marker? = null
    private var isCreatingPoint: Boolean = false
    private var lastClickedPoiId: String? = null
    private var lastClickTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val db = FirebaseDatabase.getInstance()
        locationsRef = db.getReference("localizacoes_usuarios")
        activitiesRef = db.getReference("atividades")
        poisRef = db.getReference("pontos_interesse")

        return inflater.inflate(R.layout.activity_mapa_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        recyclerViewGroupMembers = view.findViewById(R.id.recyclerViewGroupMembers)

        toolbarMembersAdapter = ToolbarMembersAdapter(groupMembersList) { lat, lng ->
            moveCameraToMember(lat, lng)
        }

        // --- LÓGICA DO BOTÃO RECENTRALIZAR ---
        val btnMinhaLocalizacao = view.findViewById<View>(R.id.btnMinhaLocalizacao)
        btnMinhaLocalizacao.setOnClickListener {
            // Usa a posição do marcador no mapa
            if (myLocationMarker != null) {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocationMarker!!.position, 16f))
            } else {
                // 2. Se for nula, tenta pedir a última localização conhecida
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val latLng = LatLng(location.latitude, location.longitude)
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                        }
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(context, "Permissão de localização é necessária.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        recyclerViewGroupMembers.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewGroupMembers.adapter = toolbarMembersAdapter

        val btnCriarPonto = view.findViewById<View>(R.id.btnCriarPonto)
        btnCriarPonto.setOnClickListener {
            handleCreatePointClick()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        // ⚠️ EXEMPLO DE FUNÇÃO DE LOGOUT
        // Coloque a chamada a essa função no seu botão de sair
        // Por exemplo: view.findViewById<Button>(R.id.btnSair).setOnClickListener { handleLogout() }
        // Se estiver em outra Activity, mova esta função para lá.

        // COMENTE O BLOCO ABAIXO, ELE É APENAS EXEMPLO
        /*
        val btnLogoutExample = view.findViewById<View>(R.id.btnLogoutExample)
        btnLogoutExample.setOnClickListener {
            handleLogout()
        }
        */
    }

    // -----------------------------------------------------------------------
    // FUNÇÃO PARA PARAR O RASTREAMENTO NO LOGOUT
    // -----------------------------------------------------------------------
    fun handleLogout() {
        val context = context ?: return

        // 1. CHAMA O MÉTODO PARA PARAR O SERVIÇO DE RASTREAMENTO
        stopLocationService()

        // 2. DESLOGA DO FIREBASE
        FirebaseAuth.getInstance().signOut()

        // 3. (OPCIONAL) Limpar dados do Firebase (removendo a última localização)
        // Se você quiser APAGAR a última localização (e não apenas parar de atualizá-la)
        // val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        // if (currentUserId != null) {
        //     locationsRef.child(currentUserId).removeValue()
        // }

        Toast.makeText(context, "Deslogado. Rastreamento interrompido.", Toast.LENGTH_LONG).show()
        // Redirecione para a tela de login aqui
        // Ex: startActivity(Intent(context, LoginActivity::class.java))
        // requireActivity().finish()
    }

    private fun handleCreatePointClick() {
        if (map == null) return

        if (currentUserGroupId == null || currentUserGroupId == NO_GROUP_ID) {
            Toast.makeText(context, "Entre em um grupo para criar pontos!", Toast.LENGTH_SHORT).show()
            return
        }

        if (isCreatingPoint) {
            tempCreationMarker?.remove()
            tempCreationMarker = null
            isCreatingPoint = false
            Toast.makeText(context, "Criação cancelada.", Toast.LENGTH_SHORT).show()
            return
        }

        val centroDaTela = map!!.cameraPosition.target
        tempCreationMarker?.remove()
        tempCreationMarker = map!!.addMarker(
            MarkerOptions()
                .position(centroDaTela)
                .title("Arraste o mapa")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .draggable(false)
        )
        tempCreationMarker?.showInfoWindow()

        isCreatingPoint = true
        Toast.makeText(context, "Mova o mapa e clique no pino VERDE para salvar.", Toast.LENGTH_LONG).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Padding para empurrar botões do Google para baixo da barra de membros
        map?.setPadding(0, 180, 0, 0)

        // *** AQUI GARANTIMOS QUE O TOOLBAR NATIVO DO GOOGLE ESTÁ ATIVADO ***
        map?.uiSettings?.isMapToolbarEnabled = true

        enableMyLocation()

        map?.setOnCameraMoveListener {
            if (isCreatingPoint && tempCreationMarker != null) {
                tempCreationMarker?.position = map!!.cameraPosition.target
            }
        }

        map?.setOnMarkerClickListener { marker ->
            val tag = marker.tag

            if (marker == tempCreationMarker && isCreatingPoint) {
                val finalPosition = tempCreationMarker!!.position
                isCreatingPoint = false // Setado como falso aqui para evitar acionamento duplo no dialog
                showAddPoiDialog(finalPosition)
                return@setOnMarkerClickListener true
            }

            if (tag is PointOfInterest) {
                val currentTime = System.currentTimeMillis()
                if (tag.id == lastClickedPoiId && (currentTime - lastClickTime) < DOUBLE_CLICK_TIME_DELTA) {
                    showEditPoiDialog(tag)
                    lastClickedPoiId = null
                    lastClickTime = 0
                    return@setOnMarkerClickListener true
                }
                lastClickedPoiId = tag.id
                lastClickTime = currentTime
                marker.showInfoWindow()

                // Retorna false para permitir que o toolbar nativo do Google Maps (rota/local) funcione
                return@setOnMarkerClickListener false
            }

            // Retorna false para permitir que o toolbar nativo do Google Maps (rota/local) funcione para membros e outros marcadores
            return@setOnMarkerClickListener false
        }
    }

    // -----------------------------------------------------------------------
    // DIALOGS
    // -----------------------------------------------------------------------
    private fun showAddPoiDialog(latLng: LatLng) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_poi, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // ⚠️ CORREÇÃO DO BUG: Limpar o marcador e o estado se o diálogo for fechado por qualquer meio (clicar fora, apertar back)
        // Isso garante que mesmo se o usuário clicar fora, o marcador some.
        dialog.setOnDismissListener {
            if (tempCreationMarker != null) {
                tempCreationMarker?.remove()
                tempCreationMarker = null
            }
            isCreatingPoint = false
        }

        val etName = dialogView.findViewById<EditText>(R.id.etPoiName)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                // Se salvar com sucesso, ele faz o dismiss e a limpeza é feita no setOnDismissListener
                savePoiToFirebase(name, latLng)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Nome inválido", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            // Se cancelar, ele faz o dismiss e a limpeza é feita no setOnDismissListener
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun savePoiToFirebase(name: String, latLng: LatLng) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val key = poisRef.push().key ?: return

        val newPoi = PointOfInterest(
            id = key,
            name = name,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            groupId = currentUserGroupId!!,
            createdBy = userId
        )
        poisRef.child(key).setValue(newPoi)
            .addOnSuccessListener {
                Toast.makeText(context, "Local salvo!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditPoiDialog(poi: PointOfInterest) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_poi, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etName = dialogView.findViewById<EditText>(R.id.etEditPoiName)
        val btnUpdate = dialogView.findViewById<TextView>(R.id.btnUpdatePoi)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDeletePoi)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelEdit)

        etName.setText(poi.name)

        btnUpdate.setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isNotEmpty()) {
                poisRef.child(poi.id).child("name").setValue(newName)
                dialog.dismiss()
            }
        }
        btnDelete.setOnClickListener {
            poisRef.child(poi.id).removeValue()
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // -----------------------------------------------------------------------
    // LISTENERS E CANAIS
    // -----------------------------------------------------------------------
    private fun listenForGroupPOIs() {
        if (currentUserGroupId == null || currentUserGroupId == NO_GROUP_ID) {
            clearPoiMarkers()
            return
        }
        if (poisValueListener != null) poisRef.removeEventListener(poisValueListener!!)

        poisValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                clearPoiMarkers()
                for (child in snapshot.children) {
                    val poi = child.getValue(PointOfInterest::class.java)
                    if (poi != null && poi.groupId == currentUserGroupId) {
                        val marker = map?.addMarker(
                            MarkerOptions()
                                .position(LatLng(poi.latitude, poi.longitude))
                                .title(poi.name)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                        if (marker != null) {
                            marker.tag = poi
                            poiMarkers.add(marker)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        poisRef.addValueEventListener(poisValueListener!!)
    }

    private fun clearPoiMarkers() {
        poiMarkers.forEach { it.remove() }
        poiMarkers.clear()
    }

    // -----------------------------------------------------------------------
    // LOCATION / SERVIÇO DE BACKGROUND
    // -----------------------------------------------------------------------
    private fun enableMyLocation() {
        // 1. Verificamos as permissões de localização
        val hasFineLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            // isMyLocationEnabled = false é usado porque estamos desenhando o nosso próprio marcador
            map?.isMyLocationEnabled = false
            setupLocationUpdates()
        } else {
            // Solicitamos Fine Location + Background Location (se API >= 29)
            val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            requestPermissions(permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation() // Tenta rodar o setup, que vai verificar as outras permissões
                } else {
                    Toast.makeText(context, "Permissão de localização é essencial para o rastreamento.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startLocationService() {
        val intent = Intent(requireContext(), LocationTrackingService::class.java)
        // Usa startForegroundService para API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    private fun stopLocationService() {
        // Esta função é chamada no handleLogout()
        val intent = Intent(requireContext(), LocationTrackingService::class.java)
        requireContext().stopService(intent)
    }

    private fun setupLocationUpdates() {
        // Verificação redundante de permissão, mas garante segurança
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        locationsRef.child(userId).child("groupId").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUserGroupId = snapshot.getValue(String::class.java)
                if (currentUserGroupId == null) {
                    currentUserGroupId = NO_GROUP_ID
                    locationsRef.child(userId).child("groupId").setValue(currentUserGroupId)
                }
                listenForGroupLocations() // CHAMA O MÉTODO ATUALIZADO
                listenForGroupPOIs()
                setupActivityGenerationListener(userId)

                // ⚠️ AQUI INICIAMOS O SERVIÇO DE RASTREAMENTO CONTÍNUO
                startLocationService()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }


    private fun moveCameraToMember(lat: Double, lng: Double) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
    }

    // -----------------------------------------------------------------------
    // GERAÇÃO DE ATIVIDADES (mantida, pois usa dados do Firebase)
    // -----------------------------------------------------------------------
    private fun setupActivityGenerationListener(userId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        activityValueListener = object : ValueEventListener {
            private var isFirstLoad = true
            private var lastNotifiedAddress: String? = null
            private var lastNotifiedLocation: Location? = null
            private var userName: String? = null

            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.key != currentUserId) return

                val currentAddress = snapshot.child("endereco").getValue(String::class.java)
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)

                if (userName == null) {
                    userName = snapshot.child("name").getValue(String::class.java)
                        ?: snapshot.child("email").getValue(String::class.java)
                                ?: "Membro"
                }

                if (isFirstLoad) {
                    val dbLastAddress = snapshot.child("lastNotifiedAddress").getValue(String::class.java)
                    lastNotifiedAddress = dbLastAddress ?: currentAddress
                    if (lat != null && lng != null) {
                        lastNotifiedLocation = Location("db").apply { latitude = lat; longitude = lng }
                    }
                    isFirstLoad = false
                    return
                }

                var distanceMoved = 0f
                val newLoc = if (lat != null && lng != null) {
                    Location("new").apply { latitude = lat; longitude = lng }
                } else null

                if (newLoc != null && lastNotifiedLocation != null) {
                    distanceMoved = lastNotifiedLocation!!.distanceTo(newLoc)
                } else if (newLoc != null) {
                    lastNotifiedLocation = newLoc
                }

                val currentAddrSafe = currentAddress ?: "Localização Indisponível"
                val lastAddrSafe = lastNotifiedAddress ?: "Localização Indisponível"

                val addressChanged = (currentAddrSafe != "Localização Indisponível" && currentAddrSafe != lastAddrSafe)
                val distanceIsSignificant = distanceMoved >= MIN_DISTANCE_FOR_ACTIVITY_M

                if (addressChanged || distanceIsSignificant) {
                    if (addressChanged && lastAddrSafe != "Localização Indisponível") {
                        val departureActivity = com.example.safetrack.model.Activity(userId, userName!!, "saiu de", lastAddrSafe, System.currentTimeMillis() - 1)
                        activitiesRef.child(userId).push().setValue(departureActivity)
                    }

                    val tipoAcao = if (addressChanged) "chegou em" else "está em"
                    val arrivalActivity = com.example.safetrack.model.Activity(userId, userName!!, tipoAcao, currentAddrSafe, System.currentTimeMillis())
                    activitiesRef.child(userId).push().setValue(arrivalActivity)

                    locationsRef.child(userId).child("lastNotifiedAddress").setValue(currentAddrSafe)
                    lastNotifiedAddress = currentAddrSafe
                    lastNotifiedLocation = newLoc
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        locationsRef.child(userId).addValueEventListener(activityValueListener!!)
    }

    @SuppressLint("MissingPermission")
    private fun listenForGroupLocations() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (otherUsersValueListener != null) {
            locationsRef.removeEventListener(otherUsersValueListener!!)
            otherUsersValueListener = null
        }

        // Limpa marcadores de outros usuários (o seu será recriado/atualizado abaixo)
        otherUserMarkers.values.forEach { it.remove() }
        otherUserMarkers.clear()

        // Sempre limpa a lista de membros no início
        groupMembersList.clear()
        toolbarMembersAdapter.notifyDataSetChanged()

        otherUsersValueListener = object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                val activeMapUids = mutableSetOf<String>()
                groupMembersList.clear() // Limpa novamente para garantir

                val mySnapshot = data.child(currentUserId)
                val myGroupId = mySnapshot.child("groupId").getValue(String::class.java)
                val myName = mySnapshot.child("name").getValue(String::class.java) ?: "Você"
                val myLat = mySnapshot.child("latitude").getValue(Double::class.java)
                val myLng = mySnapshot.child("longitude").getValue(Double::class.java)

                val isMyGroupIdValid = myGroupId != null && myGroupId != NO_GROUP_ID

                // 🎯 ALTERAÇÃO SOLICITADA: Controlar a visibilidade da RecyclerView
                if (isMyGroupIdValid) {
                    recyclerViewGroupMembers.visibility = View.VISIBLE
                } else {
                    recyclerViewGroupMembers.visibility = View.GONE
                }
                // FIM DA ALTERAÇÃO

                // ⚠️ BLOCO DE ALTERAÇÃO (EU): GARANTE QUE MEU MARCADOR E ITEM DO TOOLBAR ESTÃO SEMPRE PRESENTES
                if (myLat != null && myLng != null) {
                    val myPosition = LatLng(myLat, myLng)

                    // 1. Adiciona o usuário atual ao Toolbar de Membros APENAS se a barra estiver visível
                    if (isMyGroupIdValid) {
                        groupMembersList.add(MemberLocation(currentUserId, myName, myLat, myLng, isCurrentUser = true))
                    }

                    // 2. Atualiza ou cria o marcador do usuário atual (sempre visível)
                    if (myLocationMarker == null) {
                        myLocationMarker = map?.addMarker(
                            MarkerOptions()
                                .position(myPosition)
                                .title("Você")
                                .anchor(0.5f, 0.5f)
                                .icon(bitmapDescriptorFromVector(requireContext(), R.drawable.person))
                        )
                        if (!firstZoomDone) {
                            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(myPosition, 15f))
                            firstZoomDone = true
                        }
                    } else {
                        myLocationMarker?.position = myPosition
                    }
                    activeMapUids.add(currentUserId) // Eu estou sempre ativo no mapa
                }
                // ----------------------------------------------------------------------------------

                // LÓGICA DO GRUPO: SOMENTE ADICIONA OUTROS USUÁRIOS SE EU ESTIVER EM UM GRUPO VÁLIDO
                data.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key ?: return@forEach

                    // Ignora o usuário atual, pois já o processamos acima
                    if (userId == currentUserId) return@forEach

                    // Só processa outros usuários se o meu próprio grupo for válido
                    if (isMyGroupIdValid) {
                        val otherUserGroupId = userSnapshot.child("groupId").getValue(String::class.java)

                        // Só adiciona o marcador/item se o outro usuário estiver no MEU grupo atual
                        if (otherUserGroupId == myGroupId) {
                            val name = userSnapshot.child("name").getValue(String::class.java) ?: "Usuário"
                            val lat = userSnapshot.child("latitude").getValue(Double::class.java)
                            val lng = userSnapshot.child("longitude").getValue(Double::class.java)

                            if (lat != null && lng != null) {
                                val position = LatLng(lat, lng)

                                // Adiciona o membro à lista do toolbar
                                groupMembersList.add(MemberLocation(userId, name, lat, lng, isCurrentUser = false))
                                activeMapUids.add(userId) // O outro usuário está ativo

                                // Adiciona ou atualiza o marcador do outro usuário
                                if (otherUserMarkers.containsKey(userId)) {
                                    otherUserMarkers[userId]?.position = position
                                    otherUserMarkers[userId]?.title = name
                                } else {
                                    // 🚀 Ícone de outra pessoa
                                    val m = map?.addMarker(MarkerOptions().position(position).title(name).icon(bitmapDescriptorFromVector(requireContext(), R.drawable.person_yellow)))
                                    if (m != null) otherUserMarkers[userId] = m
                                }
                            }
                        } else {
                            // Remove marcador de quem saiu do meu grupo
                            otherUserMarkers[userId]?.remove()
                            otherUserMarkers.remove(userId)
                        }
                    } else {
                        // Se eu não estou em um grupo válido, remove todos os marcadores de outros
                        otherUserMarkers[userId]?.remove()
                        otherUserMarkers.remove(userId)
                    }
                }

                // Remove marcadores de quem saiu do grupo ou não deve mais ser exibido
                val toRemove = otherUserMarkers.keys.filter { it !in activeMapUids }
                toRemove.forEach { otherUserMarkers[it]?.remove(); otherUserMarkers.remove(it) }

                // Ordena e atualiza a toolbar (Você sempre em primeiro)
                groupMembersList.sortByDescending { it.isCurrentUser }
                toolbarMembersAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        locationsRef.addValueEventListener(otherUsersValueListener!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Remove os listeners do Firebase
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (::locationsRef.isInitialized && activityValueListener != null && currentUserId != null) {
            locationsRef.child(currentUserId).removeEventListener(activityValueListener!!)
        }
        if (::locationsRef.isInitialized && otherUsersValueListener != null) {
            locationsRef.removeEventListener(otherUsersValueListener!!)
        }
        if (::poisRef.isInitialized && poisValueListener != null) {
            poisRef.removeEventListener(poisValueListener!!)
        }

        // Limpa o marcador temporário na destruição da View, por segurança
        tempCreationMarker?.remove()
        tempCreationMarker = null
        isCreatingPoint = false
    }

    // Função para converter XML/SVG/PNG em ícone do Mapa (Com tamanho fixo)
    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val size = 96
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, size, size)
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            draw(android.graphics.Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }
}