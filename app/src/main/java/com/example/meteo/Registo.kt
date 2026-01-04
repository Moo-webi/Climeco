package com.example.meteo

data class Registo(
    val timestamp: Long = 0,
    val temperatura: Double = 0.0,
    val humidade: Double = 0.0,
    val pressao: Double = 0.0,
    val stationId: String
)
