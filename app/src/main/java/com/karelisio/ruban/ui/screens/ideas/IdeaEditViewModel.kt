package com.karelisio.ruban.ui.screens.ideas

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.repo.AlreadyGiven
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.LinkPreview
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.ui.nav.Route
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class OccasionOption(val eventId: Long, val year: Int, val label: String, val emoji: String, val date: LocalDate)

/** State of the link preview fetch. */
sealed interface LinkState {
    data object Idle : LinkState
    data object Loading : LinkState
    data class Done(val preview: LinkPreview) : LinkState
    data object Blocked : LinkState
}

@OptIn(FlowPreview::class)
class IdeaEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<Route.IdeaEdit>()
    val startWith = route.startWith

    val form = MutableStateFlow(Idea(title = "", forMe = route.forMe))
    val selected = MutableStateFlow<Set<Long>>(if (route.personId != 0L) setOf(route.personId) else emptySet())
    val link = MutableStateFlow<LinkState>(LinkState.Idle)
    val loaded = MutableStateFlow(route.id == 0L)
    private var originalImage: String? = null
    private var linkJob: Job? = null

    val persons = c.persons.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val occasions = combine(c.events.observeAll(), persons, selected) { events, people, sel ->
        val byId = people.associateBy { it.id }
        val today = LocalDate.now()
        val chosen = sel.mapNotNull(byId::get)
        val relevant = events.filter { e ->
            e.personId in sel || (e.personId == null && (chosen.isEmpty() || chosen.any { EventDates.fits(e, it) }))
        }
        EventDates.upcoming(relevant, byId, today)
            .filter { it.event.kind != EventKind.CUSTOM || it.event.personId == null || it.event.personId in sel }
            .take(10)
            .map(::toOption)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** "Already given on …" warnings, recomputed while typing. */
    val duplicates = combine(form.map { it.title to it.url }.distinctUntilChanged(), selected) { (title, url), sel -> Triple(title, url, sel) }
        .debounce(350)
        .map { (title, url, sel) ->
            if (title.length < 3 && url == null) emptyList()
            else c.ideas.alreadyGiven(sel, title, url, excludeId = form.value.id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<AlreadyGiven>())

    init {
        viewModelScope.launch {
            if (route.id != 0L) {
                c.ideas.get(route.id)?.let { item ->
                    form.value = item.idea
                    originalImage = item.idea.image
                    selected.value = item.people.map(Person::id).toSet()
                }
                loaded.value = true
            } else {
                val currency = c.settings.current().currency
                form.value = form.value.copy(currency = currency)
                if (route.eventId != 0L) {
                    c.events.get(route.eventId)?.let { e ->
                        val person = e.personId?.let { c.persons.get(it) }
                        val date = EventDates.next(e, person, LocalDate.now())
                        form.value = form.value.copy(eventId = e.id, occasionYear = date?.year)
                        if (e.personId != null && selected.value.isEmpty()) selected.value = setOf(e.personId)
                    }
                }
                route.sharedImage?.let { name -> form.value = form.value.copy(image = name) }
                route.sharedText?.let(::applySharedText)
            }
        }
    }

    private fun toOption(o: Occurrence) = OccasionOption(o.event.id, o.date.year, o.title, o.emoji, o.date)

    fun update(block: Idea.() -> Idea) {
        form.value = form.value.block()
    }

    fun togglePerson(id: Long) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
        if (selected.value.isNotEmpty() && form.value.forMe) update { copy(forMe = false) }
    }

    fun setForMe(value: Boolean) {
        update { copy(forMe = value) }
        if (value) selected.value = emptySet()
    }

    fun setOccasion(option: OccasionOption?) = update {
        copy(eventId = option?.eventId, occasionYear = option?.year, occasionLabel = null)
    }

    fun setImage(uri: Uri) = viewModelScope.launch {
        val name = c.photos.importUri(uri) ?: return@launch
        replaceImage(name)
    }

    fun setImageFile(file: File) = viewModelScope.launch {
        val name = c.photos.importFile(file) ?: return@launch
        replaceImage(name)
    }

    private fun replaceImage(name: String?) {
        val previous = form.value.image
        if (previous != null && previous != originalImage) c.photos.delete(previous)
        update { copy(image = name) }
    }

    fun removeImage() = replaceImage(null)

    /** Text shared from another app: extracts a URL if any, the rest becomes the title / notes. */
    fun applySharedText(text: String) {
        val url = LinkPreview.findUrl(text)
        val rest = url?.let { text.replace(it, "") }?.trim()?.trim('-', '|', ':', '\n', ' ') ?: text.trim()
        if (url != null) {
            update { copy(url = url, title = title.ifBlank { rest.lineSequence().firstOrNull().orEmpty().take(120) }) }
            fetchLink(url)
        } else {
            val lines = rest.lines()
            update { copy(title = title.ifBlank { lines.first().take(120) }, notes = notes.ifBlank { lines.drop(1).joinToString("\n").trim() }) }
        }
    }

    fun appendDictation(text: String) {
        val t = text.trim().replaceFirstChar { it.uppercase() }
        update { if (title.isBlank()) copy(title = t) else copy(notes = (notes + "\n" + t).trim()) }
    }

    fun fetchLink(url: String) {
        linkJob?.cancel()
        link.value = LinkState.Loading
        linkJob = viewModelScope.launch {
            val preview = c.links.fetch(url)
            if (preview == null || preview.isEmpty) {
                link.value = LinkState.Blocked
                return@launch
            }
            link.value = LinkState.Done(preview)
            val current = form.value
            val image = if (current.image == null) preview.imageUrl?.let { c.links.downloadImage(it) } else null
            update {
                copy(
                    title = if (title.isBlank() || title == url) preview.title ?: title else title,
                    priceCents = priceCents ?: preview.priceCents,
                    currency = if (priceCents == null && preview.currency != null) preview.currency else currency,
                    image = image ?: this.image,
                    url = preview.canonicalUrl ?: this.url,
                )
            }
        }
    }

    suspend fun save(): Long {
        val idea = form.value.copy(title = form.value.title.trim())
        if (originalImage != null && originalImage != idea.image) c.photos.delete(originalImage)
        val id = c.ideas.save(idea, selected.value)
        // First price point for the price history.
        if (idea.id == 0L && idea.priceCents != null && idea.url != null) {
            c.db.prices().insert(com.karelisio.ruban.data.model.PricePoint(ideaId = id, priceCents = idea.priceCents, currency = idea.currency))
        }
        return id
    }

    /** Called when leaving without saving: drops the pictures imported for nothing. */
    fun discard() {
        val image = form.value.image
        if (image != null && image != originalImage) c.photos.delete(image)
    }
}
