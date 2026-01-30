package com.oakiha.audia.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object SettingsCategory : Screen("settings_category/{categoryId}") {
        fun createRoute(categoryId: String) = "settings_category/$categoryId"
    }
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object BooklistDetail : Screen("Booklist_detail/{BooklistId}") {
        fun createRoute(BooklistId: String) = "Booklist_detail/$BooklistId"
    }

    object  DailyMixScreen : Screen("daily_mix")
    object Stats : Screen("stats")
    object CategoryDetail : Screen("Category_detail/{CategoryId}") { // New screen
        fun createRoute(CategoryId: String) = "Category_detail/$CategoryId"
    }
    object DJSpace : Screen("dj_space")
    // La ruta base es "Book_detail". La ruta completa con el argumento se define en AppNavigation.
    object BookDetail : Screen("Book_detail/{BookId}") {
        // FunciÃƒÂ³n de ayuda para construir la ruta de navegaciÃƒÂ³n con el ID del ÃƒÂ¡lbum.
        fun createRoute(BookId: Long) = "Book_detail/$BookId"
    }

    object AuthorDetail : Screen("Author_detail/{AuthorId}") {
        fun createRoute(AuthorId: Long) = "Author_detail/$AuthorId"
    }

    object EditTransition : Screen("edit_transition?BooklistId={BooklistId}") {
        fun createRoute(BooklistId: String?) =
            if (BooklistId != null) "edit_transition?BooklistId=$BooklistId" else "edit_transition"
    }

    object About : Screen("about")

    object Authorsettings : Screen("Author_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object Equalizer : Screen("equalizer")

}
