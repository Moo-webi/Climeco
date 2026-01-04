package com.example.meteo

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        loadDataFromFirebase()
    }

    private fun loadDataFromFirebase() {
        val db = FirebaseFirestore.getInstance()
        val stations = listOf("STATION_01", "STATION_02", "STATION_03")

        val listaRegistos = mutableListOf<Registo>()
        var pending = stations.size

        for (stationId in stations) {
            db.collection(stationId)
                .get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val temperatura = document.getDouble("temperatura") ?: 0.0
                        val humidade = document.getDouble("humidade") ?: 0.0
                        val pressao = document.getDouble("pressao") ?: 0.0
                        val timestampSec = document.getLong("timestamp") ?: continue

                        listaRegistos.add(
                            Registo(
                                timestamp = timestampSec * 1000L,
                                temperatura = temperatura,
                                humidade = humidade,
                                pressao = pressao,
                                stationId = stationId
                            )
                        )
                    }

                    pending--
                    if (pending == 0) {
                        displayData(listaRegistos.sortedBy { it.timestamp })
                    }
                }
                .addOnFailureListener {
                    pending--
                    if (pending == 0) {
                        displayData(listaRegistos.sortedBy { it.timestamp })
                    }
                }
        }
    }


    private fun displayData(lista: List<Registo>) {
        Log.d("DATA", "Total registos: ${lista.size}")

        for (r in lista) {
            Log.d("DATA", "T=${r.temperatura}  H=${r.humidade}  TS=${r.timestamp}")
        }

        // TODO: Send data to chart
        // TODO: Calculate statistics
        // TODO: Update UI
    }
}
