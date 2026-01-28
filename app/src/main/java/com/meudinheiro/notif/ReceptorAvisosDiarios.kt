package com.meudinheiro.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReceptorAvisosDiarios : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AgendadorNotifDespesas.runNow(context)
    }
}
