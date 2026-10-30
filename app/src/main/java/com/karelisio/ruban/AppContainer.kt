package com.karelisio.ruban

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karelisio.ruban.data.backup.BackupManager
import com.karelisio.ruban.data.db.RubanDatabase
import com.karelisio.ruban.data.net.LinkExtractor
import com.karelisio.ruban.data.repo.EventRepository
import com.karelisio.ruban.data.repo.IdeaRepository
import com.karelisio.ruban.data.repo.PersonRepository
import com.karelisio.ruban.data.repo.PhotoStore
import com.karelisio.ruban.data.repo.PriceTracker
import com.karelisio.ruban.data.repo.ReceivedRepository
import com.karelisio.ruban.data.repo.SuggestionRepository
import com.karelisio.ruban.data.settings.SettingsRepository
import com.karelisio.ruban.data.update.UpdateManager
import com.karelisio.ruban.notify.Notifications
import com.karelisio.ruban.notify.ReminderScheduler
import com.karelisio.ruban.widget.WidgetUpdater
import com.karelisio.ruban.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container, created once by [RubanApp]. */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: RubanDatabase by lazy { RubanDatabase.build(context) }
    val settings by lazy { SettingsRepository(context) }
    val photos by lazy { PhotoStore(context) }
    val persons by lazy { PersonRepository(db, photos) }
    val events by lazy { EventRepository(db) }
    val ideas by lazy { IdeaRepository(db, photos) }
    val received by lazy { ReceivedRepository(db, photos) }
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    val links by lazy { LinkExtractor(http, photos) }
    val reminders by lazy { ReminderScheduler(context, this) }
    val suggestions by lazy { SuggestionRepository(context, db, settings) }
    val prices by lazy { PriceTracker(db, links) }
    val backup by lazy { BackupManager(context, this) }
    val updates by lazy { UpdateManager(context, http, settings) }
}

class RubanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        WorkScheduler.ensure(this)
        container.scope.launch {
            val settings = container.settings.current()
            if (!settings.holidaysSeeded) {
                container.events.seedHolidays()
                container.settings.setHolidaysSeeded(true)
            }
            runCatching { container.suggestions.ensureLoaded() }
        }
        observeReminderInputs()
    }

    /** Any change to events, people or reminder settings reschedules the next alarm. */
    @OptIn(FlowPreview::class)
    private fun observeReminderInputs() {
        val c = container
        combine(
            c.events.observeAll(),
            c.persons.observeAll(),
            c.settings.settings.map { Triple(it.reminderOffsets, it.reminderHour, it.reminderMinute) }.distinctUntilChanged(),
        ) { _, _, _ -> Unit }
            .debounce(800)
            .onEach { c.reminders.reschedule() }
            .launchIn(c.scope)
        // Widgets follow every change that affects the next event and its state.
        combine(c.events.observeAll(), c.persons.observeAll(), c.ideas.observeAll()) { _, _, _ -> Unit }
            .debounce(1_500)
            .onEach { WidgetUpdater.refresh(this) }
            .launchIn(c.scope)
    }
}

val Context.container: AppContainer get() = (applicationContext as RubanApp).container

/** ViewModel factory backed by the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> rubanViewModel(
    key: String? = null,
    crossinline create: AppContainer.(SavedStateHandle) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer { (this[APPLICATION_KEY] as RubanApp).container.create(createSavedStateHandle()) }
    },
)
