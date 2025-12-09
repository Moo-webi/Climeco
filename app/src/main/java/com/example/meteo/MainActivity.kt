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

        db.collection("STATION_01")
            .get()
            .addOnSuccessListener { result ->

                val listaRegistos = mutableListOf<Registo>()

                for (document in result) {
                    val temperatura = document.getDouble("temperatura") ?: 0.0
                    val humidade = document.getDouble("humidade") ?: 0.0
                    val timestamp = document.getLong("timestamp") ?: 0L

                    listaRegistos.add(
                        Registo(
                            timestamp = timestamp,
                            temperatura = temperatura,
                            humidade = humidade
                        )
                    )
                }

                // 🔥 Now do something useful with the data
                displayData(listaRegistos)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Erro ao ler dados", e)
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
