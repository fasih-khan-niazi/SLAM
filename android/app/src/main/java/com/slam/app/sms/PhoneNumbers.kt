package com.slam.app.sms

object PhoneNumbers {
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    fun last10(raw: String): String {
        val d = digits(raw)
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun matches(stored: String, incoming: String): Boolean {
        val a = last10(stored)
        val b = last10(incoming)
        return a.isNotBlank() && a == b
    }

    fun isValid(raw: String): Boolean {
        val d = digits(raw)
        return d.length in 10..15
    }
}
