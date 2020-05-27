/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.rum.RumAttributes
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class RumEventSerializer : Serializer<RumEvent> {

    private val simpleDateFormat = SimpleDateFormat(ISO_8601, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // region Serializer

    override fun serialize(model: RumEvent): String {
        val root = JsonObject()

        // Event Context
        val context = model.context
        root.addProperty(RumAttributes._DD_FORMAT_VERSION, FORMAT_VERSION)
        root.addProperty(RumAttributes.APPLICATION_ID, context.applicationId)
        root.addProperty(RumAttributes.SESSION_ID, context.sessionId)
        root.addProperty(RumAttributes.SESSION_TYPE, SESSION_TYPE_USER)
        root.addProperty(RumAttributes.VIEW_ID, context.viewId)

        // Timestamp
        val formattedDate = simpleDateFormat.format(Date(model.timestamp))
        root.addProperty(RumAttributes.DATE, formattedDate)

        // User Info
        addUserInfo(model, root)

        // Network Info
        addNetworkInfo(model, root)

        // Data
        addEventData(model.eventData, root)

        // custom attributes
        addCustomAttributes(model, root)

        return root.toString()
    }

    // endregion

    // region Internal

    private fun addUserInfo(model: RumEvent, root: JsonObject) {
        val email = model.userInfo.email
        val id = model.userInfo.id
        val name = model.userInfo.name

        if (!email.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_EMAIL, email)
        }
        if (!id.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_ID, id)
        }
        if (!name.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_NAME, name)
        }
    }

    private fun addNetworkInfo(model: RumEvent, root: JsonObject) {
        val info = model.networkInfo
        if (info != null) {
            root.addProperty(RumAttributes.NETWORK_CONNECTIVITY, info.connectivity.serialized)
            if (!info.carrierName.isNullOrBlank()) {
                root.addProperty(RumAttributes.NETWORK_CARRIER_NAME, info.carrierName)
            }
            if (info.carrierId >= 0) {
                root.addProperty(RumAttributes.NETWORK_CARRIER_ID, info.carrierId)
            }
            if (info.upKbps >= 0) {
                root.addProperty(RumAttributes.NETWORK_UP_KBPS, info.upKbps)
            }
            if (info.downKbps >= 0) {
                root.addProperty(RumAttributes.NETWORK_DOWN_KBPS, info.downKbps)
            }
            if (info.strength > Int.MIN_VALUE) {
                root.addProperty(RumAttributes.NETWORK_SIGNAL_STRENGTH, info.strength)
            }
        }
    }

    private fun addEventData(eventData: RumEventData, root: JsonObject) {
        root.addProperty(RumAttributes.TYPE, eventData.category)
        when (eventData) {
            is RumEventData.Resource -> addResourceData(eventData, root)
            is RumEventData.Action -> addActionData(eventData, root)
            is RumEventData.View -> addViewData(eventData, root)
            is RumEventData.Error -> addErrorData(root, eventData)
        }
    }

    private fun addViewData(
        eventData: RumEventData.View,
        root: JsonObject
    ) {
        root.addProperty(RumAttributes._DD_DOCUMENT_VERSION, eventData.version)
        root.addProperty(RumAttributes.VIEW_URL, eventData.name)
        root.addProperty(RumAttributes.VIEW_DURATION, eventData.durationNanoSeconds)
        root.addProperty(RumAttributes.VIEW_ERROR_COUNT, eventData.errorCount)
        root.addProperty(RumAttributes.VIEW_RESOURCE_COUNT, eventData.resourceCount)
        root.addProperty(RumAttributes.VIEW_ACTION_COUNT, eventData.actionCount)
    }

    private fun addActionData(
        eventData: RumEventData.Action,
        root: JsonObject
    ) {
        root.addProperty(RumAttributes.ACTION_TYPE, eventData.type)
        root.addProperty(RumAttributes.ACTION_ID, eventData.id.toString())
        root.addProperty(RumAttributes.ACTION_DURATION, eventData.durationNanoSeconds)
    }

    private fun addResourceData(
        eventData: RumEventData.Resource,
        root: JsonObject
    ) {
        root.addProperty(RumAttributes.RESOURCE_DURATION, eventData.durationNanoSeconds)
        root.addProperty(RumAttributes.RESOURCE_TYPE, eventData.type.value)
        root.addProperty(RumAttributes.RESOURCE_METHOD, eventData.method)
        root.addProperty(RumAttributes.RESOURCE_URL, eventData.url)
        val timing = eventData.timing
        if (timing != null) {
            if (timing.dnsStart > 0) {
                root.addProperty(RumAttributes.RESOURCE_TIMING_DNS_START, timing.dnsStart)
                root.addProperty(RumAttributes.RESOURCE_TIMING_DNS_DURATION, timing.dnsDuration)
            }
            if (timing.connectStart > 0) {
                root.addProperty(RumAttributes.RESOURCE_TIMING_CONNECT_START, timing.connectStart)
                root.addProperty(
                    RumAttributes.RESOURCE_TIMING_CONNECT_DURATION,
                    timing.connectDuration
                )
            }
            if (timing.sslStart > 0) {
                root.addProperty(RumAttributes.RESOURCE_TIMING_SSL_START, timing.sslStart)
                root.addProperty(RumAttributes.RESOURCE_TIMING_SSL_DURATION, timing.sslDuration)
            }
            if (timing.firstByteStart > 0) {
                root.addProperty(RumAttributes.RESOURCE_TIMING_FB_START, timing.firstByteStart)
                root.addProperty(
                    RumAttributes.RESOURCE_TIMING_FB_DURATION,
                    timing.firstByteDuration
                )
            }
            if (timing.downloadStart > 0) {
                root.addProperty(RumAttributes.RESOURCE_TIMING_DL_START, timing.downloadStart)
                root.addProperty(RumAttributes.RESOURCE_TIMING_DL_DURATION, timing.downloadDuration)
            }
        }
    }

    private fun addErrorData(
        root: JsonObject,
        eventData: RumEventData.Error
    ) {
        root.addProperty(RumAttributes.ERROR_MESSAGE, eventData.message)
        root.addProperty(RumAttributes.ERROR_SOURCE, eventData.source)

        val throwable = eventData.throwable
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            root.addProperty(RumAttributes.ERROR_TYPE, throwable.javaClass.simpleName)
            root.addProperty(RumAttributes.ERROR_STACK, sw.toString())
        }
    }

    private fun addCustomAttributes(
        event: RumEvent,
        jsonEvent: JsonObject
    ) {
        event.attributes.forEach {
            val value = it.value
            val jsonValue = when (value) {
                null -> JsonNull.INSTANCE
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                is Float -> JsonPrimitive(value)
                is Double -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Date -> JsonPrimitive(value.time)
                is JsonObject -> value
                is JsonArray -> value
                else -> JsonPrimitive(value.toString())
            }
            jsonEvent.add(it.key, jsonValue)
        }
    }

    // endregion

    companion object {
        private const val FORMAT_VERSION = 2
        private const val SESSION_TYPE_USER = "user"
        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }
}