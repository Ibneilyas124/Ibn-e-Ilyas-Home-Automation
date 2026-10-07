package com.ibneilyas.home.data

import com.ibneilyas.home.domain.HomeData
import kotlinx.coroutines.flow.StateFlow

/** UI talks only to this interface. Mock now, real ESP32 (HTTP) in Phase 3. */
interface HomeRepository {
    val data: StateFlow<HomeData>
    suspend fun toggle(applianceId: String)

    /** Auto timer: after [seconds] set the appliance to [on]. seconds = 0 cancels. */
    suspend fun setTimer(applianceId: String, seconds: Int, on: Boolean): Boolean = false
}
