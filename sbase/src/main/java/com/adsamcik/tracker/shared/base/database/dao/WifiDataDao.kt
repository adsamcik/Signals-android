package com.adsamcik.tracker.shared.base.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.adsamcik.tracker.shared.base.database.data.DatabaseWifiData
import com.adsamcik.tracker.shared.base.database.data.DateRange
import com.adsamcik.tracker.shared.base.database.data.location.TimeLocation2DWeighted

@Dao
interface WifiDataDao : BaseDao<DatabaseWifiData> {
	@Query("DELETE FROM tracker_session")
	fun deleteAll()

	@Query(
			"""
		UPDATE wifi_data
		SET longitude = :longitude, latitude = :latitude, altitude = :altitude, level = :level
		WHERE bssid = :bssid AND level < :level"""
	)
	fun updateSignalStrength(
			bssid: String,
			longitude: Double,
			latitude: Double,
			altitude: Double?,
			level: Int
	)

	@Query(
			"""
		UPDATE wifi_data
		SET last_seen = :lastSeen, ssid = :ssid, capabilities = :capabilities, frequency = :frequency
		WHERE bssid = :bssid
		"""
	)
	fun updateData(
			bssid: String,
			ssid: String,
			capabilities: String,
			frequency: Int,
			lastSeen: Long
	)

	@Transaction
	fun upsert(objList: Collection<DatabaseWifiData>) {
		val insertResult = insert(objList)
		val updateList = objList.filterIndexed { index, _ -> insertResult[index] == -1L }

		updateList.forEach {
			if (it.longitude != null && it.latitude != null) {
				updateSignalStrength(it.bssid, it.longitude, it.latitude, it.altitude, it.level)
			}
			updateData(it.bssid, it.ssid, it.capabilities, it.frequency, it.lastSeen)
		}
	}

	@Query("SELECT * from wifi_data")
	fun getAll(): List<DatabaseWifiData>

	@Query("SELECT * from wifi_data LIMIT :count")
	fun getAll(count: Long): List<DatabaseWifiData>

	@RawQuery
	fun getAll(query: SupportSQLiteQuery): List<DatabaseWifiData>

	@Query(
			"""
		SELECT last_seen as time, latitude as lat, longitude as lon, COUNT(*) as weight FROM wifi_data
		WHERE latitude >= :bottomLatitude and latitude <= :topLatitude
			and longitude >= :leftLongitude  and longitude <= :rightLongitude
		GROUP BY lat, lon
		"""
	)
	fun getAllInside(
			topLatitude: Double,
			rightLongitude: Double,
			bottomLatitude: Double,
			leftLongitude: Double
	): List<TimeLocation2DWeighted>

	@Query(
			"""
		SELECT last_seen as time, latitude as lat, longitude as lon, COUNT(*) as weight FROM wifi_data
		WHERE last_seen >= :from and last_seen <= :to
			and latitude >= :bottomLatitude and longitude >= :leftLongitude
			and latitude <= :topLatitude and longitude <= :rightLongitude
		GROUP BY lat, lon
	"""
	)
	fun getAllInsideAndBetween(
			from: Long,
			to: Long,
			topLatitude: Double,
			rightLongitude: Double,
			bottomLatitude: Double,
			leftLongitude: Double
	): List<TimeLocation2DWeighted>

	@Query("SELECT COUNT(*) from wifi_data")
	fun count(): Long

	@Query("SELECT MIN(last_seen) as start, MAX(last_seen) as endInclusive from wifi_data")
	fun range(): DateRange
}
