package com.eto.predictor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ResultBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ETO      = "eto"
        private const val ARG_R2       = "r2"
        private const val ARG_RMSE     = "rmse"
        private const val ARG_FEATURES = "features"
        private const val ARG_RANK     = "rank"
        private const val ARG_WARNINGS = "warnings"
        private const val ARG_LOC      = "loc"

        fun newInstance(
            eto: Double,
            r2: Double,
            rmse: Double,
            features: String,
            rank: Int,
            warnings: List<String>,
            loc: String
        ): ResultBottomSheet {
            return ResultBottomSheet().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_ETO, eto)
                    putDouble(ARG_R2, r2)
                    putDouble(ARG_RMSE, rmse)
                    putString(ARG_FEATURES, features)
                    putInt(ARG_RANK, rank)
                    putStringArrayList(ARG_WARNINGS, ArrayList(warnings))
                    putString(ARG_LOC, loc)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_result_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val eto      = arguments?.getDouble(ARG_ETO)      ?: 0.0
        val r2       = arguments?.getDouble(ARG_R2)       ?: 0.0
        val rmse     = arguments?.getDouble(ARG_RMSE)     ?: 0.0
        val features = arguments?.getString(ARG_FEATURES) ?: ""
        val rank     = arguments?.getInt(ARG_RANK)        ?: 0
        val warnings = arguments?.getStringArrayList(ARG_WARNINGS) ?: arrayListOf()
        val loc      = arguments?.getString(ARG_LOC)      ?: ""

        // ── Big ETo value ──────────────────────────────────────────────
        view.findViewById<TextView>(R.id.bsEtoValue).text =
            String.format("%.2f", eto)

        // ── Irrigation status label + color ───────────────────────────
        val labelView = view.findViewById<TextView>(R.id.bsEtoLabel)
        val (label, colorHex) = when {
            eto < 2.0 -> Pair("🟢 Low ETo — Minimal irrigation needed",      "#39FF14")
            eto < 4.0 -> Pair("🟡 Moderate ETo — Normal irrigation",          "#FFE600")
            eto < 6.0 -> Pair("🟠 High ETo — Increased irrigation needed",    "#FF6D00")
            else      -> Pair("🔴 Very High ETo — Heavy irrigation needed",   "#FF1744")
        }
        labelView.text = label
        labelView.setTextColor(android.graphics.Color.parseColor(colorHex))

        // ── Model info ────────────────────────────────────────────────
        view.findViewById<TextView>(R.id.bsModelRank).text = "Rank #$rank"
        view.findViewById<TextView>(R.id.bsModelR2).text =
            "R²: ${"%.4f".format(r2)}"
        view.findViewById<TextView>(R.id.bsModelRmse).text =
            "RMSE: ${"%.4f".format(rmse)} mm/day"
        view.findViewById<TextView>(R.id.bsModelFeatures).text =
            "Features: $features"

        // ── Location chip ──────────────────────────────────────────────
        view.findViewById<TextView>(R.id.bsLocationUsed).apply {
            text = if (loc.isBlank()) "Manual Input" else loc
            visibility = View.VISIBLE
        }

        // ── Warnings (if any) ─────────────────────────────────────────
        val warningView = view.findViewById<TextView>(R.id.bsWarnings)
        if (warnings.isNotEmpty()) {
            warningView.visibility = View.VISIBLE
            warningView.text = warnings.joinToString("\n") { "⚠️ $it" }
        } else {
            warningView.visibility = View.GONE
        }

        // ── Close button ──────────────────────────────────────────────
        view.findViewById<Button>(R.id.bsBtnClose).setOnClickListener {
            dismiss()
        }
    }
}
