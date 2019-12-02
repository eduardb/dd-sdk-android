package com.datadog.android.log.internal.utils

import com.datadog.android.BuildConfig
import com.datadog.android.log.Logger

internal const val SDK_LOG_PREFIX = "DD_LOG"
/**
 * Global SDK Logger. This logger is meant for debugging purposes. Should not post logs
 * to Datadog endpoint and should be conditioned by the BuildConfig flag.
 */
internal val sdkLogger: Logger = buildLogger()

internal fun buildLogger(): Logger {
    return Logger.Builder()
        .setDatadogLogsEnabled(false)
        .setLogcatLogsEnabled(BuildConfig.LOGCAT_ENABLED)
        .setServiceName(SDK_LOG_PREFIX)
        .build()
}
