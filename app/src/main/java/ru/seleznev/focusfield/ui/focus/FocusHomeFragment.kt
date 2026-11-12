package ru.seleznev.focusfield.ui.focus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.biometrics.FaceAnalyzer
import ru.seleznev.focusfield.data.model.FocusBand
import ru.seleznev.focusfield.data.state.BlockingStateStore
import ru.seleznev.focusfield.databinding.FragmentFocusHomeBinding
import ru.seleznev.focusfield.ui.common.appContainer

/**
 * Главный экран: запуск/остановка сессии концентрации, живой индикатор внимания,
 * превью камеры с анализом моргания и выражения лица.
 */
class FocusHomeFragment : Fragment() {

    private var _binding: FragmentFocusHomeBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: FocusViewModel by activityViewModels { appContainer.viewModelFactory }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                binding.cameraHint.isVisible = true
                binding.cameraPreview.isVisible = false
            }
        }

    private var cameraProvider: ProcessCameraProvider? = null
    private var faceAnalyzer: FaceAnalyzer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTaskDropdown()
        setupButtons()

        viewModel.score.observe(viewLifecycleOwner) { score -> renderScore(score) }
        viewModel.sessionActive.observe(viewLifecycleOwner) { active -> renderSessionState(active) }
        viewModel.elapsedSeconds.observe(viewLifecycleOwner) { seconds ->
            binding.elapsed.text = formatElapsed(seconds)
        }
        viewModel.wearData.observe(viewLifecycleOwner) { data ->
            binding.wearChip.isVisible = data != null
            data?.let {
                binding.wearChip.text = getString(R.string.wear_chip_text, it.heartRate, it.steps)
            }
        }

        setupCamera()
    }

    private fun setupTaskDropdown() {
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            val labels = mutableListOf(getString(R.string.task_none))
            val ids = mutableListOf(0L)
            tasks.filter { !it.isDone }.forEach { task ->
                labels.add(task.title)
                ids.add(task.id)
            }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            binding.taskDropdown.setAdapter(adapter)
            binding.taskDropdown.setOnItemClickListener { _, _, position, _ ->
                viewModel.selectTask(ids.getOrNull(position) ?: 0L)
            }
        }
    }

    private fun setupButtons() {
        binding.btnToggleSession.setOnClickListener {
            if (viewModel.sessionActive.value == true) {
                viewModel.stopSession()
            } else {
                viewModel.startSession()
            }
        }
        binding.btnOpenBlocklist.setOnClickListener {
            findNavController().navigate(R.id.action_focusHome_to_blockedItems)
        }
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlayPermission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
        }
    }

    private fun renderScore(score: Int) {
        val display = if (score == BlockingStateStore.NO_DATA) {
            getString(R.string.score_no_data)
        } else {
            getString(R.string.score_percent, score)
        }
        binding.scoreValue.text = display
        binding.scoreBar.setProgressCompat(if (score > 0) score else 0, true)
        val color = when (FocusBand.fromScore(score)) {
            FocusBand.HIGH -> R.color.focus_high
            FocusBand.MEDIUM -> R.color.focus_medium
            FocusBand.LOW -> R.color.focus_low
            FocusBand.NO_DATA -> R.color.focus_unknown
        }
        binding.scoreBar.setIndicatorColor(ContextCompat.getColor(requireContext(), color))
        binding.scoreLabel.text = when (FocusBand.fromScore(score)) {
            FocusBand.HIGH -> getString(R.string.band_high)
            FocusBand.MEDIUM -> getString(R.string.band_medium)
            FocusBand.LOW -> getString(R.string.band_low)
            FocusBand.NO_DATA -> getString(R.string.band_no_data)
        }
    }

    private fun renderSessionState(active: Boolean) {
        binding.btnToggleSession.text = getString(
            if (active) R.string.session_stop else R.string.session_start
        )
        binding.sessionCard.isVisible = active
    }

    private fun setupCamera() {
        if (!viewModel.cameraMonitoringEnabled) {
            binding.cameraHint.isVisible = true
            binding.cameraPreview.isVisible = false
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                faceAnalyzer = FaceAnalyzer { frame -> viewModel.onFaceFrame(frame) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(requireContext()),
                            requireNotNull(faceAnalyzer)
                        )
                    }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.camera_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun formatElapsed(seconds: Long): String {
        val minutes = seconds / 60
        val rest = seconds % 60
        return getString(R.string.elapsed_format, minutes, rest)
    }

    override fun onDestroyView() {
        faceAnalyzer?.release()
        cameraProvider?.unbindAll()
        cameraProvider = null
        faceAnalyzer = null
        _binding = null
        super.onDestroyView()
    }
}
