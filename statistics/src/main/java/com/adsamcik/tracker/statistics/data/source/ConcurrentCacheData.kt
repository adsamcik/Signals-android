package com.adsamcik.tracker.statistics.data.source

import java.util.concurrent.locks.Lock

/**
 * Object providing lock for thread safe caching
 */
data class ConcurrentCacheData<Producer>(
		val lock: Lock,
		val producer: Producer,
		var data: Any?
)
