package com.meudinheiro.funcoes

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {

    fun vibrar(context: Context, tipo: String) {
        // Pega o serviço de vibração correto dependendo do Android
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        when (tipo) {
            // 🟢 EXTRATO / DEPÓSITO: Duplo clique rápido e alegre (Sensação de "Moeda caindo")
            "sucesso" -> {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 20, 50, 30), // Tempos: Espera, Vibra, Espera, Vibra
                    intArrayOf(0, 150, 0, 255), // Força da vibração (0 a 255)
                    -1 // Não repete
                )
                vibrator.vibrate(effect)
            }

            // 🔴 MINHA CONTA / ALERTA: Uma batida seca e pesada (Sensação de impacto/alerta)
            "impacto" -> {
                val effect = VibrationEffect.createOneShot(50, 255) // Curto, mas com força máxima
                vibrator.vibrate(effect)
            }

            // 🟣 METAS: Uma vibração crescente (Sensação de "Carregando Energia")
            "energia" -> {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 15, 15, 15, 25),
                    intArrayOf(0, 50, 100, 180, 255), // A força vai subindo!
                    -1
                )
                vibrator.vibrate(effect)
            }

            // 🟠 TRANSFERÊNCIA: Um "Zzzt" rápido e cortante (Sensação de movimento/envio)
            "movimento" -> {
                val effect = VibrationEffect.createOneShot(30, 120)
                vibrator.vibrate(effect)
            }

            // 💠 NÚCLEO CENTRAL (Power Core): Clique sutil de engrenagem
            "click_menu" -> {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                vibrator.vibrate(effect)
            }
        }
    }
}