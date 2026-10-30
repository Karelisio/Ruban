package com.karelisio.ruban

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.karelisio.ruban.ui.nav.Route

/** Turns external intents (share menu, widget, tile, shortcuts, notifications) into a destination. */
object IntentRouter {
    const val ACTION_NEW_IDEA = "com.karelisio.ruban.action.NEW_IDEA"
    const val ACTION_OPEN = "com.karelisio.ruban.action.OPEN"
    const val EXTRA_START = "start"
    const val EXTRA_IDEA_ID = "idea_id"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_TAB = "tab"

    fun newIdea(context: Context, start: String? = null): Intent =
        Intent(ACTION_NEW_IDEA).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (start != null) putExtra(EXTRA_START, start) }

    fun openIdea(context: Context, id: Long): Intent =
        Intent(ACTION_OPEN).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_IDEA_ID, id)

    fun openEvent(context: Context, id: Long): Intent =
        Intent(ACTION_OPEN).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_EVENT_ID, id)

    fun openTab(context: Context, tab: Int): Intent =
        Intent(ACTION_OPEN).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TAB, tab)

    suspend fun route(context: Context, intent: Intent?): Route? {
        intent ?: return null
        return when (intent.action) {
            ACTION_NEW_IDEA -> Route.IdeaEdit(startWith = intent.getStringExtra(EXTRA_START), eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0))
            ACTION_OPEN -> when {
                intent.hasExtra(EXTRA_IDEA_ID) -> Route.IdeaDetail(intent.getLongExtra(EXTRA_IDEA_ID, 0))
                intent.hasExtra(EXTRA_EVENT_ID) -> Route.EventDetail(intent.getLongExtra(EXTRA_EVENT_ID, 0))
                intent.hasExtra(EXTRA_TAB) -> Route.Home(intent.getIntExtra(EXTRA_TAB, 0))
                else -> null
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> shared(context, intent)
            else -> null
        }
    }

    private suspend fun shared(context: Context, intent: Intent): Route? {
        val type = intent.type.orEmpty()
        if (type.startsWith("image/")) {
            val uri = intent.imageUri() ?: return null
            val name = context.container.photos.importUri(uri)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            return Route.IdeaEdit(sharedImage = name, sharedText = text)
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (text.isEmpty() && subject.isEmpty()) return null
        // Browsers put the page title in the subject and the URL in the text.
        val combined = if (subject.isNotEmpty() && !text.contains(subject)) "$subject\n$text" else text.ifEmpty { subject }
        return Route.IdeaEdit(sharedText = combined)
    }

    @Suppress("DEPRECATION")
    private fun Intent.imageUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?: getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
        } else {
            (getParcelableExtra<Uri>(Intent.EXTRA_STREAM)) ?: getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
        }
}
