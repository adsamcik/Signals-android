package com.adsamcik.tracker.common.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.adsamcik.tracker.common.data.SessionActivity

@Dao
interface ActivityDao : BaseDao<SessionActivity> {
	@Query("SELECT * FROM activity")
	fun getAll(): List<SessionActivity>

	@Query("SELECT * FROM activity WHERE id >= 0")
	fun getAllUser(): List<SessionActivity>

	@Query("SELECT * FROM activity WHERE id = :id")
	fun get(id: Long): SessionActivity?

	@Query("SELECT * FROM activity WHERE name = :name")
	fun find(name: String): SessionActivity?

	@Query("DELETE FROM activity WHERE id = :id")
	fun delete(id: Long)
}
