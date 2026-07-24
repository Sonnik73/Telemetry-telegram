/*
 * Copyright 2026 Georgii Ippolitov (g000sha256)
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
 * Describes a connection of a bot to an account.
 *
 * @property bot Information about the bot.
 * @property connectionDate Point in time (Unix timestamp) when the bot was added; may be 0 if unknown.
 * @property deviceModel Model of the device that was used for the bot connection, as provided by the application; may be empty if unknown.
 * @property location A human-readable description of the location from which the bot was connected, based on the IP address; may be empty if unknown.
 */
public class BusinessConnectedBotInfo public constructor(
    public val bot: BusinessConnectedBot,
    public val connectionDate: Int,
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
        other as BusinessConnectedBotInfo
        if (other.bot != bot) {
            return false
        }
        if (other.connectionDate != connectionDate) {
            return false
        }
        if (other.deviceModel != deviceModel) {
            return false
        }
        return other.location == location
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + bot.hashCode()
        hashCode = 31 * hashCode + connectionDate.hashCode()
        hashCode = 31 * hashCode + deviceModel.hashCode()
        hashCode = 31 * hashCode + location.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("BusinessConnectedBotInfo")
            append("(")
            append("bot=")
            append(bot)
            append(", ")
            append("connectionDate=")
            append(connectionDate)
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
