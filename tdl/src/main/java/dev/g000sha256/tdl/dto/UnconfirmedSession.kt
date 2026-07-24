/*
 * Copyright 2025-2026 Georgii Ippolitov (g000sha256)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.g000sha256.tdl.dto

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * Contains information about an unconfirmed session.
 *
 * @property type Session type.
 * @property date Point in time (Unix timestamp) when the user has logged in or the business bot was connected.
 * @property deviceModel Model of the device that was used for the session creation, as provided by the application.
 * @property location A human-readable description of the location from which the session was created, based on the IP address.
 */
public class UnconfirmedSession public constructor(
    public val type: SessionType,
    public val date: Int,
    public val deviceModel: String,
    public val location: String,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other == null) {
            return false
        }
        if (other::class != this::class) {
            return false
        }
        other as UnconfirmedSession
        if (other.type != type) {
            return false
        }
        if (other.date != date) {
            return false
        }
        if (other.deviceModel != deviceModel) {
            return false
        }
        return other.location == location
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + type.hashCode()
        hashCode = 31 * hashCode + date.hashCode()
        hashCode = 31 * hashCode + deviceModel.hashCode()
        hashCode = 31 * hashCode + location.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("UnconfirmedSession")
            append("(")
            append("type=")
            append(type)
            append(", ")
            append("date=")
            append(date)
            append(", ")
            append("deviceModel=")
            append(deviceModel)
            append(", ")
            append("location=")
            append(location)
            append(")")
        }
    }
}
