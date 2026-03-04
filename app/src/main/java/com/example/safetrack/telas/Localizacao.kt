package com.example.safetrack.model

data class Localizacao(
    val groupId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long? = null,
    // Campo enviado pelo MapaFragment
    val endereco: String? = null,
    // Campo usado para controle da notificação
    val lastNotifiedAddress: String? = null
)