package com.example.safetrack.model

data class Activity(
    val userId: String = "",
    val userName: String = "",
    val tipo: String = "", // "chegou em" ou "saiu de"
    val localNome: String = "",
    val timestamp: Long = 0
) {
    // Construtor vazio necessário para o Firebase Realtime Database
    constructor() : this("", "", "", "", 0)
}