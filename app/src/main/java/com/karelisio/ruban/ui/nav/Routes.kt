package com.karelisio.ruban.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data class Home(val tab: Int = 0) : Route
    @Serializable data object Onboarding : Route

    @Serializable data class PersonDetail(val id: Long) : Route
    @Serializable data class PersonEdit(val id: Long = 0) : Route

    @Serializable data class IdeaDetail(val id: Long) : Route
    @Serializable data class IdeaEdit(
        val id: Long = 0,
        val personId: Long = 0,
        val eventId: Long = 0,
        val forMe: Boolean = false,
        val sharedText: String? = null,
        val sharedImage: String? = null,
        val startWith: String? = null,
    ) : Route

    @Serializable data class EventDetail(val id: Long) : Route
    @Serializable data class EventEdit(val id: Long = 0, val personId: Long = 0) : Route

    @Serializable data class YearRecap(val year: Int) : Route
    @Serializable data class Inspire(val personId: Long, val eventId: Long = 0) : Route

    @Serializable data object Received : Route
    @Serializable data class ReceivedEdit(val id: Long = 0) : Route

    @Serializable data object Settings : Route
    @Serializable data object Permissions : Route
    @Serializable data object Backup : Route
}

/** Tabs of the home screen. */
enum class HomeTab { IDEAS, PEOPLE, EVENTS, BUDGET }

object CaptureStart {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val VOICE = "voice"
}
