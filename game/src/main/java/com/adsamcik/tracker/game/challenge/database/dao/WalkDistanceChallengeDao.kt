package com.adsamcik.tracker.game.challenge.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.adsamcik.tracker.common.database.dao.BaseDao
import com.adsamcik.tracker.game.challenge.data.entity.WalkDistanceChallengeEntity

@Dao
interface WalkDistanceChallengeDao : BaseDao<WalkDistanceChallengeEntity> {
	@Query("SELECT * FROM challenge_walk_distance WHERE id == :id")
	fun get(id: Long): WalkDistanceChallengeEntity

	@Query("SELECT * FROM challenge_walk_distance WHERE entry_id == :entryId")
	fun getByEntry(entryId: Long): WalkDistanceChallengeEntity
}
