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
import com.karelisio.ruban.data.db.RubanDatabase
import com.karelisio.ruban.data.repo.EventRepository
import com.karelisio.ruban.data.repo.IdeaRepository
import com.karelisio.ruban.data.repo.PersonRepository
import com.karelisio.ruban.data.repo.PhotoStore
import com.karelisio.ruban.data.repo.ReceivedRepository
import com.karelisio.ruban.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
}

class RubanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            val settings = container.settings.current()
            if (!settings.holidaysSeeded) {
                container.events.seedHolidays()
                container.settings.setHolidaysSeeded(true)
            }
        }
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
