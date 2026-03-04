package com.example.safetrack

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.safetrack.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationsRef: DatabaseReference
    // Não inicializamos userId aqui, pois ele pode mudar ou ser nulo
    // Vamos verificá-lo dentro do LocationCallback

    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "SAFE_TRACK_LOCATION_CHANNEL"
    private val LOCATION_REQUEST_INTERVAL = 5000L // 5 segundos
    private var lastCalculationLocation: Location? = null // Para cálculo de distância diária

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationsRef = FirebaseDatabase.getInstance().getReference("localizacoes_usuarios")

        // Não é mais necessário inicializar userId aqui, pois faremos a verificação
        // a cada atualização de localização.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Se o usuário não estiver logado ao iniciar, pare imediatamente.
        if (initialUserId.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Inicia o Serviço em Primeiro Plano (Notificação)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 2. Inicia a Coleta de Localização
        setupLocationUpdates()

        // 3. O sistema tentará reiniciar o serviço se ele for morto
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_REQUEST_INTERVAL)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // ⚠️ VERIFICAÇÃO CRÍTICA DE USUÁRIO LOGADO AQUI
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId.isNullOrEmpty()) {
                    // Se o usuário deslogou enquanto o serviço estava ativo, pare o serviço.
                    stopSelf()
                    return
                }

                val location = locationResult.lastLocation ?: return

                // 1. Atualiza Distância Diária
                updateDailyDistance(location)

                // 2. Geocodificação (Obter Endereço)
                val fullAddress = reverseGeocodeLocation(location.latitude, location.longitude)

                // 3. Envia para o Firebase
                val updates = mutableMapOf<String, Any>(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "timestamp" to System.currentTimeMillis(),
                    "endereco" to fullAddress
                )
                // Usa o ID recém-verificado
                locationsRef.child(currentUserId).updateChildren(updates)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Isso deve ser tratado antes de chamar o serviço, mas é um bom backup.
            stopSelf()
        }
    }

    private fun updateDailyDistance(currentLocation: Location) {
        // A lógica de updateDailyDistance precisa ser movida para aqui
        // Como a lógica original usa um SingleValueEvent, o que pode ser ineficiente no loop
        // do LocationCallback, esta função precisa ser revisada, mas manteremos a essência:

        // TODO: A lógica original de updateDailyDistance precisa ser adaptada para ser eficiente no Service.
        // A forma mais eficiente é usar um SharedPreferences ou um Transaction no Database.

        // Mantendo a lógica mais próxima do original (simplificado para não usar listener síncrono):
        val MIN_DISTANCE_FOR_DAILY_CALC = 5.0

        if (lastCalculationLocation != null) {
            val dist = lastCalculationLocation!!.distanceTo(currentLocation)
            if (dist > MIN_DISTANCE_FOR_DAILY_CALC) {
                // Aqui você deve fazer a soma no Firebase ou em um cache local.
                // Para este exemplo, apenas atualizamos a última localização.
            }
        }
        lastCalculationLocation = currentLocation
    }

    // Função de Geocodificação (movida do Fragmento)
    private fun reverseGeocodeLocation(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val addr = addresses[0]
                val street = addr.thoroughfare ?: ""
                val number = addr.subThoroughfare ?: ""
                val area = addr.subAdminArea ?: ""
                val full = if (street.isNotEmpty()) "$street, $number - $area" else area
                full.replace("null", "").trim().ifEmpty { "Local desconhecido" }
            } else "Localização Indisponível"
        } catch (e: Exception) {
            "Localização Indisponível"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Para parar a coleta de localização
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Funções para a Notificação do Foreground Service
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Serviço SafeTrack", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeTrack Ativo")
            .setContentText("Monitorando localização em segundo plano.")
            .setSmallIcon(R.drawable.person) // Use um ícone do seu projeto
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}