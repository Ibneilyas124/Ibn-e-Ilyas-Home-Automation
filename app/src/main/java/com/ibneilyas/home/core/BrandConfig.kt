package com.ibneilyas.home.core

/** Permanent brand. Compile-time constant: not in DB, not in models, not in exports. */
object BrandConfig {
    const val COMPANY_NAME = "Ibn e Ilyas Technologies"
    const val DEFAULT_SUBTITLE = "Sarfraz Qureshi Home Automation"
    const val OWNER_NAME = "Sarfraz Qureshi"
    const val REQUIRE_GENUINE_CONTROLLER = false
    /** DEV ONLY: true skips the flash ticket check. Set to false before release. */
    const val DEV_SKIP_TICKET = true
    /** Public address of the Ibn cloud Worker (no secret in it). */
    const val CLOUD_URL = "https://ibn-home-cloud.ahmedsarfraz650.workers.dev"
}
