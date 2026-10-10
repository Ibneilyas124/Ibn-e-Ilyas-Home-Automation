package com.ibneilyas.home.data

import com.ibneilyas.home.domain.HomeData
import com.ibneilyas.home.domain.SchedEntry
import kotlinx.coroutines.flow.StateFlow

/** UI talks only to this interface. Mock now, real ESP32 (HTTP) in Phase 3. */
interface HomeRepository {
    val data: StateFlow<HomeData>
    suspend fun toggle(applianceId: String)

    /** Auto timer: after [seconds] set the appliance to [on]. seconds = 0 cancels. */
    suspend fun setTimer(applianceId: String, seconds: Int, on: Boolean): Boolean = false

    /** Schedules stored on one ESP32 (null = could not be read). */
    suspend fun schedules(nodeId: String): List<SchedEntry>? = null

    /** Writes the full schedule list of one ESP32. Returns what the device now holds, or null on failure. */
    suspend fun saveSchedules(nodeId: String, list: List<SchedEntry>): List<SchedEntry>? = null
}
