package com.adsamcik.tracker.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adsamcik.tracker.common.database.dao.GenericPreferenceDao
import com.adsamcik.tracker.common.database.dao.NotificationPreferenceDao
import com.adsamcik.tracker.common.database.data.GenericPreference
import com.adsamcik.tracker.common.database.data.NotificationPreference

@Database(
		entities = [GenericPreference::class, NotificationPreference::class],
		version = 1
)
abstract class PreferenceDatabase : RoomDatabase() {
	abstract val genericDao: GenericPreferenceDao

	abstract val notificationDao: NotificationPreferenceDao

	companion object : ObjectBaseDatabase<PreferenceDatabase>(PreferenceDatabase::class.java) {
		override val databaseName: String = "preference_database"

		override fun setupDatabase(database: Builder<PreferenceDatabase>) = Unit

	}
}