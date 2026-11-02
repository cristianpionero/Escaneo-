package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {

    private val symbols = DecimalFormatSymbols(Locale("es", "CO")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val decimalFormat = DecimalFormat("#,###", symbols)

    /**
     * Formatea un valor numérico a pesos colombianos (COP).
     * Ejemplos:
     *  0 -> "$ 0"
     *  100 -> "$ 100"
     *  1000 -> "$ 1.000"
     *  2500 -> "$ 2.500"
     *  10000 -> "$ 10.000"
     *  150000 -> "$ 150.000"
     */
    fun formatCop(amount: Long): String {
        return "$ ${decimalFormat.format(amount)}"
    }

    /**
     * Parsea una cadena de texto extrayendo solo los dígitos numéricos
     * para evitar errores de tipeo.
     */
    fun parseCopInput(input: String): Long {
        val digitsOnly = input.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return 0L
        return digitsOnly.toLongOrNull() ?: 0L
    }
}
