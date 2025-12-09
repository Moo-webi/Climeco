package com.example.meteo

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

class DashboardActivity : AppCompatActivity() {

    private lateinit var spinnerVariavel: Spinner
    private lateinit var btnDataInicio: Button
    private lateinit var btnDataFim: Button
    private lateinit var btnCarregar: Button
    private lateinit var chart: LineChart

    private lateinit var txtMedia: TextView
    private lateinit var txtMax: TextView
    private lateinit var txtMin: TextView
    private lateinit var txtTendencia: TextView

    private var dataInicio: Long? = null
    private var dataFim: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        initViews()
        initListeners()
    }

    private fun initViews() {
        spinnerVariavel = findViewById(R.id.spinnerVariavel)
        btnDataInicio = findViewById(R.id.btnDataInicio)
        btnDataFim = findViewById(R.id.btnDataFim)
        btnCarregar = findViewById(R.id.btnCarregar)
        chart = findViewById(R.id.chart)

        txtMedia = findViewById(R.id.txtMedia)
        txtMax = findViewById(R.id.txtMax)
        txtMin = findViewById(R.id.txtMin)
        txtTendencia = findViewById(R.id.txtTendencia)
    }

    private fun initListeners() {

        btnDataInicio.setOnClickListener {
            escolherData { timestamp ->
                dataInicio = timestamp
                btnDataInicio.text = "Início: " + formatDate(timestamp)
            }
        }

        btnDataFim.setOnClickListener {
            escolherData { timestamp ->
                dataFim = timestamp
                btnDataFim.text = "Fim: " + formatDate(timestamp)
            }
        }

        btnCarregar.setOnClickListener {
            Log.d("DEBUG_SPINNER", "Selecionado: ${spinnerVariavel.selectedItem}")
            carregarDados()
        }
    }

    private fun escolherData(onSelect: (Long) -> Unit) {
        val agora = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d, 0, 0)
                onSelect(cal.timeInMillis)
            },
            agora.get(Calendar.YEAR),
            agora.get(Calendar.MONTH),
            agora.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDate(ts: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun carregarDados() {

        if (dataInicio == null || dataFim == null) {
            Toast.makeText(this, "Selecione intervalo de datas!", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        db.collection("STATION_01")
            .get()
            .addOnSuccessListener { result ->

                val lista = mutableListOf<Registo>()

                Log.d("DEBUG_FIREBASE", "TOTAL DOCS = ${result.size()}")

                for (document in result) {

                    Log.d("DEBUG_FIREBASE", "DOC DATA = ${document.data}")

                    val tsRaw = document.getLong("timestamp")
                    Log.d("DEBUG_FIREBASE", "TS RAW = $tsRaw")

                    val ts = (tsRaw ?: continue) * 1000L
                    val temp = document.getDouble("temperatura") ?: 0.0
                    val hum = document.getDouble("humidade") ?: 0.0

                    // Range filter
                    if (ts in dataInicio!!..dataFim!!) {
                        lista.add(Registo(ts, temp, hum))
                        Log.d("DEBUG_FIREBASE", "ADDED — ts=$ts temp=$temp hum=$hum")
                    } else {
                        Log.d("DEBUG_FIREBASE", "IGNORED (OUT OF RANGE) — ts=$ts")
                    }
                }

                Log.d("DEBUG_FIREBASE", "FILTERED DOCS = ${lista.size}")

                processarDados(lista)
            }
            .addOnFailureListener { e ->
                Log.e("Dashboard", "Erro ao ler Firebase", e)
                Toast.makeText(this, "Erro ao carregar dados!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processarDados(lista: List<Registo>) {

        if (lista.isEmpty()) {
            Log.d("DEBUG_DATA", "Lista vazia após filtro")
            return
        }

        val ordenados = lista.sortedBy { it.timestamp }
        val variavel = spinnerVariavel.selectedItem.toString()

        val valores = when (variavel) {
            "Temperatura" -> ordenados.map { it.temperatura }
            "Humidade" -> ordenados.map { it.humidade }
            else -> {
                Log.d("DEBUG_DATA", "Variável inválida no spinner")
                emptyList()
            }
        }

        val timestamps = ordenados.map { it.timestamp }

        Log.d("DEBUG_DATA", "VALORES = $valores")
        Log.d("DEBUG_DATA", "TIMESTAMPS = $timestamps")

        mostrarGrafico(timestamps, valores, variavel)

        val stats = calcularEstatisticas(valores)
        mostrarEstatisticas(stats)
    }

    private fun calcularEstatisticas(valores: List<Double>): Map<String, Any> {
        if (valores.isEmpty()) {
            return mapOf("media" to 0.0, "max" to 0.0, "min" to 0.0, "tendencia" to "Sem dados")
        }

        val media = valores.average()
        val max = valores.maxOrNull() ?: 0.0
        val min = valores.minOrNull() ?: 0.0

        val tendencia = when {
            valores.last() > valores.first() -> "Ascendente"
            valores.last() < valores.first() -> "Descendente"
            else -> "Estável"
        }

        return mapOf(
            "media" to media,
            "max" to max,
            "min" to min,
            "tendencia" to tendencia
        )
    }

    private fun mostrarEstatisticas(stats: Map<String, Any>) {
        txtMedia.text = "Média: %.2f".format(stats["media"] as Double)
        txtMax.text = "Máximo: %.2f".format(stats["max"] as Double)
        txtMin.text = "Mínimo: %.2f".format(stats["min"] as Double)
        txtTendencia.text = "Tendência: ${stats["tendencia"]}"
    }

    private fun mostrarGrafico(timestamps: List<Long>, valores: List<Double>, label: String) {

        if (timestamps.isEmpty() || valores.isEmpty()) {
            Log.d("DEBUG_GRAPH", "Timestamp ou valores vazios — gráfico não pode ser desenhado")
            return
        }

        val entries = valores.mapIndexed { index, value ->
            Entry(index.toFloat(), value.toFloat())
        }

        Log.d("DEBUG_GRAPH", "ENTRIES = $entries")

        val dataSet = LineDataSet(entries, label).apply {
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircles(false)
            setDrawValues(false)
        }

        chart.data = LineData(dataSet)

        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index in timestamps.indices)
                    sdf.format(Date(timestamps[index]))
                else ""
            }
        }

        chart.xAxis.granularity = 1f
        chart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false

        chart.invalidate()
    }
}
