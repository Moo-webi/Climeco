package com.example.meteo

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var chipGroupVariavel: ChipGroup
    private lateinit var btnPeriodoA: MaterialButton
    private lateinit var btnPeriodoB: MaterialButton
    private lateinit var btnCarregar: MaterialButton
    private lateinit var txtEstado: TextView
    private lateinit var chart: LineChart

    private lateinit var txtMediaA: TextView
    private lateinit var txtMaxA: TextView
    private lateinit var txtMinA: TextView
    private lateinit var txtTendenciaA: TextView

    private lateinit var txtMediaB: TextView
    private lateinit var txtMaxB: TextView
    private lateinit var txtMinB: TextView
    private lateinit var txtTendenciaB: TextView

    // Intervalos (ms)
    private var inicioA: Long? = null
    private var fimA: Long? = null
    private var inicioB: Long? = null
    private var fimB: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        initViews()
        initDefaults()
        initListeners()
        limparUI("Escolhe uma variável e seleciona os dois períodos para comparar.")
    }

    private fun initViews() {
        chipGroupVariavel = findViewById(R.id.chipGroupVariavel)
        btnPeriodoA = findViewById(R.id.btnPeriodoA)
        btnPeriodoB = findViewById(R.id.btnPeriodoB)
        btnCarregar = findViewById(R.id.btnCarregar)
        txtEstado = findViewById(R.id.txtEstado)
        chart = findViewById(R.id.chart)

        txtMediaA = findViewById(R.id.txtMediaA)
        txtMaxA = findViewById(R.id.txtMaxA)
        txtMinA = findViewById(R.id.txtMinA)
        txtTendenciaA = findViewById(R.id.txtTendenciaA)

        txtMediaB = findViewById(R.id.txtMediaB)
        txtMaxB = findViewById(R.id.txtMaxB)
        txtMinB = findViewById(R.id.txtMinB)
        txtTendenciaB = findViewById(R.id.txtTendenciaB)
    }

    private fun initDefaults() {
        chipGroupVariavel.check(R.id.chipTemp) // default: temperatura
    }

    private fun initListeners() {
        btnPeriodoA.setOnClickListener {
            escolherIntervalo("Período A") { ini, fim ->
                inicioA = ini
                fimA = fim
                btnPeriodoA.text = "A: ${formatDate(ini)} → ${formatDate(fim)}"
                atualizarEstado()
            }
        }

        btnPeriodoB.setOnClickListener {
            escolherIntervalo("Período B") { ini, fim ->
                inicioB = ini
                fimB = fim
                btnPeriodoB.text = "B: ${formatDate(ini)} → ${formatDate(fim)}"
                atualizarEstado()
            }
        }

        btnCarregar.setOnClickListener { compararPeriodos() }
    }

    private fun escolherIntervalo(titulo: String, onPick: (Long, Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Selecionar $titulo")
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            val ini = range.first
            val fim = range.second
            onPick(ini, fim)
        }

        picker.show(supportFragmentManager, "DATE_RANGE_$titulo")
    }

    private fun atualizarEstado() {
        val aOk = inicioA != null && fimA != null
        val bOk = inicioB != null && fimB != null

        txtEstado.text = when {
            !aOk && !bOk -> "Seleciona o Período A e o Período B."
            aOk && !bOk -> "Período A definido. Seleciona o Período B."
            !aOk && bOk -> "Período B definido. Seleciona o Período A."
            else -> "Tudo pronto. Carrega em “Comparar”."
        }
    }

    private fun formatDate(ts: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun compararPeriodos() {
        val aIni = inicioA ?: return
        val aFim = fimA ?: return
        val bIni = inicioB ?: return
        val bFim = fimB ?: return

        val db = FirebaseFirestore.getInstance()
        txtEstado.text = "A carregar dados…"

        val stations = listOf("STATION_01", "STATION_02", "STATION_03")
        val todos = mutableListOf<Registo>()
        var pending = stations.size

        for (stationId in stations) {
            db.collection(stationId)
                .get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val tsRaw = document.getLong("timestamp") ?: continue
                        val tsMs = tsRaw * 1000L

                        todos.add(
                            Registo(
                                timestamp = tsMs,
                                temperatura = document.getDouble("temperatura") ?: 0.0,
                                humidade = document.getDouble("humidade") ?: 0.0,
                                pressao = document.getDouble("pressao") ?: 0.0,
                                stationId = stationId
                            )
                        )
                    }

                    pending--
                    if (pending == 0) {
                        processarComparacao(todos, aIni, aFim, bIni, bFim)
                    }
                }
                .addOnFailureListener {
                    pending--
                    if (pending == 0) {
                        processarComparacao(todos, aIni, aFim, bIni, bFim)
                    }
                }
        }
    }


    private fun processarComparacao(todos: List<Registo>, aIni: Long, aFim: Long, bIni: Long, bFim: Long) {

        val periodoA = todos.filter { it.timestamp in aIni..aFim }.sortedBy { it.timestamp }
        val periodoB = todos.filter { it.timestamp in bIni..bFim }.sortedBy { it.timestamp }

        if (periodoA.isEmpty() && periodoB.isEmpty()) {
            limparUI("Sem dados nos dois períodos selecionados.")
            return
        }

        val label: String
        val unidade: String
        val valoresA: List<Double>
        val valoresB: List<Double>
        val tsA: List<Long>
        val tsB: List<Long>

        when (chipGroupVariavel.checkedChipId) {
            R.id.chipTemp -> {
                label = "Temperatura"; unidade = "°C"
                valoresA = periodoA.map { it.temperatura }
                valoresB = periodoB.map { it.temperatura }
                tsA = periodoA.map { it.timestamp }
                tsB = periodoB.map { it.timestamp }
            }
            R.id.chipHum -> {
                label = "Humidade"; unidade = "%"
                valoresA = periodoA.map { it.humidade }
                valoresB = periodoB.map { it.humidade }
                tsA = periodoA.map { it.timestamp }
                tsB = periodoB.map { it.timestamp }
            }
            R.id.chipPres -> {
                label = "Pressão"; unidade = "hPa"
                valoresA = periodoA.map { it.pressao }
                valoresB = periodoB.map { it.pressao }
                tsA = periodoA.map { it.timestamp }
                tsB = periodoB.map { it.timestamp }
            }
            else -> {
                limparUI("Escolhe uma variável válida.")
                return
            }
        }

        txtEstado.text = when {
            valoresA.isEmpty() -> "Sem dados no Período A. A mostrar Período B."
            valoresB.isEmpty() -> "Sem dados no Período B. A mostrar Período A."
            else -> "$label — comparação A vs B"
        }

        mostrarGraficoComparacao(
            tsA, valoresA, "A: $label ($unidade)",
            tsB, valoresB, "B: $label ($unidade)"
        )

        if (valoresA.isNotEmpty()) mostrarEstatisticas(calcularEstatisticas(valoresA), unidade, lado = "A")
        else limparStats("A")

        if (valoresB.isNotEmpty()) mostrarEstatisticas(calcularEstatisticas(valoresB), unidade, lado = "B")
        else limparStats("B")
    }

    private fun limparUI(msg: String) {
        txtEstado.text = msg
        limparStats("A")
        limparStats("B")
        chart.clear()
        chart.invalidate()
    }

    private fun limparStats(lado: String) {
        if (lado == "A") {
            txtMediaA.text = "Média: --"
            txtMaxA.text = "Máximo: --"
            txtMinA.text = "Mínimo: --"
            txtTendenciaA.text = "Tendência: --"
        } else {
            txtMediaB.text = "Média: --"
            txtMaxB.text = "Máximo: --"
            txtMinB.text = "Mínimo: --"
            txtTendenciaB.text = "Tendência: --"
        }
    }

    private fun calcularEstatisticas(valores: List<Double>): Map<String, Any> {
        val media = valores.average()
        val max = valores.maxOrNull() ?: 0.0
        val min = valores.minOrNull() ?: 0.0

        val tendencia = when {
            valores.last() > valores.first() -> "Ascendente"
            valores.last() < valores.first() -> "Descendente"
            else -> "Estável"
        }

        return mapOf("media" to media, "max" to max, "min" to min, "tendencia" to tendencia)
    }

    private fun mostrarEstatisticas(stats: Map<String, Any>, unidade: String, lado: String) {
        val mediaTxt = "Média: %.2f %s".format(stats["media"] as Double, unidade).trim()
        val maxTxt = "Máximo: %.2f %s".format(stats["max"] as Double, unidade).trim()
        val minTxt = "Mínimo: %.2f %s".format(stats["min"] as Double, unidade).trim()
        val tendTxt = "Tendência: ${stats["tendencia"]}"

        if (lado == "A") {
            txtMediaA.text = mediaTxt
            txtMaxA.text = maxTxt
            txtMinA.text = minTxt
            txtTendenciaA.text = tendTxt
        } else {
            txtMediaB.text = mediaTxt
            txtMaxB.text = maxTxt
            txtMinB.text = minTxt
            txtTendenciaB.text = tendTxt
        }
    }

    private fun mostrarGraficoComparacao(
        tsA: List<Long>, valoresA: List<Double>, labelA: String,
        tsB: List<Long>, valoresB: List<Double>, labelB: String
    ) {
        chart.clear()

        val dataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>()

        if (valoresA.isNotEmpty()) {
            val entriesA = valoresA.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
            val dsA = LineDataSet(entriesA, labelA).apply {
                lineWidth = 2.5f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawCircles(false)
                setDrawValues(false)
            }
            dataSets.add(dsA)
        }

        if (valoresB.isNotEmpty()) {
            val entriesB = valoresB.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
            val dsB = LineDataSet(entriesB, labelB).apply {
                lineWidth = 2.5f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawCircles(false)
                setDrawValues(false)
            }
            dataSets.add(dsB)
        }

        chart.data = LineData(dataSets)

        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val eixoRef = if (tsA.isNotEmpty()) tsA else tsB

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in eixoRef.indices) sdf.format(Date(eixoRef[i])) else ""
            }
        }

        chart.xAxis.granularity = 1f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        chart.invalidate()
    }
}
