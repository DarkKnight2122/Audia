package com.oakiha.audia.ui.glancewidget

import android.content.Context
import android.content.Intent
import com.oakiha.audia.MainActivity

object IntentProvider {
    fun mainActivityIntent(context: Context): Intent {
        val intent = Intent(context, MainActivity::class.java)
        // ACTION_MAIN y CATEGORY_LAUNCHER son tÃ­picos para iniciar la actividad principal.
        // Si la app ya estÃ¡ en ejecuciÃ³n, estos flags ayudan a traerla al frente.
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        // FLAG_ACTIVITY_NEW_TASK es necesario si se inicia desde un context que no es de Activity (como un AppWidgetProvider).
        // FLAG_ACTIVITY_REORDER_TO_FRONT traerÃ¡ la tarea existente al frente si ya estÃ¡ ejecutÃ¡ndose,
        // en lugar de lanzar una nueva instancia encima si el launchMode lo permite.
        // Si MainActivity tiene launchMode="singleTop", onNewIntent serÃ¡ llamado si ya estÃ¡ en la cima.
        // Si tiene launchMode="singleTask" o "singleInstance", se comportarÃ¡ segÃºn esos modos.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        return intent
    }
}
