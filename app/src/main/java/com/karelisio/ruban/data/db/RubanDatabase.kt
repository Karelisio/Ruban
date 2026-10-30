package com.karelisio.ruban.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.IdeaPerson
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.data.model.Suggestion
import com.karelisio.ruban.data.model.SuggestionVerdict

@Database(
    entities = [
        Person::class, Event::class, Idea::class, IdeaPerson::class, PricePoint::class,
        ReceivedGift::class, Suggestion::class, SuggestionVerdict::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RubanDatabase : RoomDatabase() {
    abstract fun persons(): PersonDao
    abstract fun events(): EventDao
    abstract fun ideas(): IdeaDao
    abstract fun prices(): PriceDao
    abstract fun received(): ReceivedDao
    abstract fun suggestions(): SuggestionDao

    companion object {
        const val NAME = "ruban.db"

        fun build(context: Context): RubanDatabase =
            Room.databaseBuilder(context, RubanDatabase::class.java, NAME).build()
    }
}
