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
        private const val ARG_ETO    = "eto"
        private const val ARG_PARAMS = "params"
        private const val ARG_LOC    = "loc"

        fun newInstance(eto: Double, params: Int, loc: String): ResultBottomSheet {
            return ResultBottomSheet().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_ETO, eto)
                    putInt(ARG_PARAMS, params)
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

        val eto    = arguments?.getDouble(ARG_ETO)    ?: 0.0
        val params = arguments?.getInt(ARG_PARAMS)    ?: 0
        val loc    = arguments?.getString(ARG_LOC)    ?: ""

        // ── Big ETo number ────────────────────────────────────────
        view.findViewById<TextView>(R.id.bsEtoValue).text =
            String.format("%.2f", eto)

        // ── Params chip ───────────────────────────────────────────
        view.findViewById<TextView>(R.id.bsParamsUsed).text =
            "$params / 6"

        // ── Location chip ─────────────────────────────────────────
        view.findViewById<TextView>(R.id.bsLocationUsed).apply {
            text       = if (loc.isBlank()) "Manual Input" else loc
            visibility = View.VISIBLE
        }

        // ── Status label + color ──────────────────────────────────
        val labelView = view.findViewById<TextView>(R.id.bsEtoLabel)
        val (label, colorHex) = when {
            eto < 2.0 -> Pair("🟢   Low ETo — Minimal irrigation needed",   "#39FF14")
            eto < 4.0 -> Pair("🟡   Moderate ETo — Normal irrigation",       "#FFE600")
            eto < 6.0 -> Pair("🟠   High ETo — Increased irrigation needed", "#FF6D00")
            else      -> Pair("🔴   Very High ETo — Heavy irrigation needed", "#FF1744")
        }
        labelView.text = label
        labelView.setTextColor(android.graphics.Color.parseColor(colorHex))

        // ── Close button ──────────────────────────────────────────
        view.findViewById<Button>(R.id.bsBtnClose).setOnClickListener {
            dismiss()
        }
    }
}
