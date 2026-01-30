package com.oakiha.audia.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.oakiha.audia.MainActivity
import com.oakiha.audia.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dynamic app shortcuts for the launcher.
 */
@Singleton
class AppShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SHORTCUT_ID_LAST_Booklist = "last_Booklist"
    }

    /**
     * Updates the dynamic shortcut for the last played Booklist.
     * @param BooklistId The ID of the Booklist
     * @param BooklistName The display name of the Booklist
     */
    fun updateLastBooklistshortcut(BooklistId: String, BooklistName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return // Shortcuts not supported before API 25
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_Booklist
            putExtra(MainActivity.EXTRA_Booklist_ID, BooklistId)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_LAST_Booklist)
            .setShortLabel(BooklistName)
            .setLongLabel(BooklistName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_Booklist_purple))
            .setIntent(intent)
            .build()

        // Remove old shortcut first to force icon refresh
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_LAST_Booklist))
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Removes the last Booklist shortcut if it exists.
     */
    fun removeLastBooklistshortcut() {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_LAST_Booklist))
    }
}
