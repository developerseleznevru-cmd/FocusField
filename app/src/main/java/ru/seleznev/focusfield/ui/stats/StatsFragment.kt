package ru.seleznev.focusfield.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.data.model.DayAverage
import ru.seleznev.focusfield.data.model.FocusBand
import ru.seleznev.focusfield.data.model.FocusSummary
import ru.seleznev.focusfield.databinding.FragmentStatsBinding
import ru.seleznev.focusfield.ui.common.appContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Статистика и отчёты: гистограмма концентрации по дням,
 * круговая диаграмма распределения внимания, сводка за период.
 */
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: StatsViewModel by viewModels { appContainer.viewModelFactory }

    private val dayFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCharts()
        setupRangeToggle()

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats == null) return@observe
            renderBarChart(stats.dayAverages)
            renderPieChart(stats.distribution)
            renderSummary(stats.summary)
        }
        viewModel.range.observe(viewLifecycleOwner) { range ->
            when (range) {
                StatsRange.DAY -> binding.rangeToggle.check(R.id.range_day)
                StatsRange.WEEK -> binding.rangeToggle.check(R.id.range_week)
                StatsRange.MONTH -> binding.rangeToggle.check(R.id.range_month)
            }
        }
    }

    private fun setupRangeToggle() {
        binding.rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val range = when (checkedId) {
                R.id.range_day -> StatsRange.DAY
                R.id.range_week -> StatsRange.WEEK
                else -> StatsRange.MONTH
            }
            if (viewModel.range.value != range) viewModel.setRange(range)
        }
    }

    private fun setupCharts() {
        binding.barChart.description.isEnabled = false
        binding.barChart.axisLeft.axisMinimum = 0f
        binding.barChart.axisLeft.axisMaximum = 100f
        binding.barChart.axisRight.isEnabled = false
        binding.barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.barChart.xAxis.granularity = 1f
        binding.barChart.legend.isEnabled = false

        binding.pieChart.description.isEnabled = false
        binding.pieChart.setUsePercentValues(true)
        binding.pieChart.legend.isEnabled = false
    }

    private fun renderBarChart(dayAverages: List<DayAverage>) {
        val entries = dayAverages.mapIndexed { index, day ->
            BarEntry(index.toFloat(), if (day.average < 0) 0f else day.average.toFloat())
        }
        val dataSet = BarDataSet(entries, getString(R.string.stats_chart_title)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.chart_primary)
            setDrawValues(false)
        }
        binding.barChart.data = BarData(dataSet).apply { barWidth = 0.6f }
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(
            dayAverages.map { dayFormat.format(Date(it.dayStart)) }
        )
        binding.barChart.animateY(500)
        binding.barChart.invalidate()
    }

    private fun renderPieChart(distribution: Map<FocusBand, Int>) {
        val entries = listOf(
            PieEntry(distribution[FocusBand.HIGH]?.toFloat() ?: 0f, getString(R.string.band_high)),
            PieEntry(distribution[FocusBand.MEDIUM]?.toFloat() ?: 0f, getString(R.string.band_medium)),
            PieEntry(distribution[FocusBand.LOW]?.toFloat() ?: 0f, getString(R.string.band_low))
        )
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(requireContext(), R.color.focus_high),
                ContextCompat.getColor(requireContext(), R.color.focus_medium),
                ContextCompat.getColor(requireContext(), R.color.focus_low)
            )
            sliceSpace = 2f
        }
        binding.pieChart.data = PieData(dataSet).apply { setDrawValues(false) }
        binding.pieChart.animateY(500)
        binding.pieChart.invalidate()
    }

    private fun renderSummary(summary: FocusSummary) {
        binding.summarySessions.text = getString(R.string.stats_sessions_count, summary.sessions)
        binding.summaryMinutes.text = getString(R.string.stats_total_minutes, summary.totalMinutes)
        binding.summaryAvg.text =
            if (summary.avgScore >= 0) getString(R.string.score_percent, summary.avgScore)
            else getString(R.string.score_no_data)
        binding.summaryInterruptions.text =
            getString(R.string.stats_interruptions, summary.interruptions)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
