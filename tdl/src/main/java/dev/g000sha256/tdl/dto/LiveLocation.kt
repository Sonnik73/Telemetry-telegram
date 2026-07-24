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
 * A live location.
 *
 * @property location The current location.
 * @property livePeriod Time relative to the message send date, for which the location can be updated, in seconds; if 0x7FFFFFFF, then location can be updated forever.
 * @property heading The direction in which the location moves, in degrees; 1-360; 0 if unknown.
 * @property proximityAlertRadius The maximum distance to another chat member for proximity alerts, in meters (0-100000). 0 if the notification is disabled. Can't be enabled in direct messages chats, channels and Saved Messages. Available only to the message sender.
 */
public class LiveLocation public constructor(
    public val location: Location,
    public val livePeriod: Int,
    public val heading: Int,
    public val proximityAlertRadius: Int,
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
        other as LiveLocation
        if (other.location != location) {
            return false
        }
        if (other.livePeriod != livePeriod) {
            return false
        }
        if (other.heading != heading) {
            return false
        }
        return other.proximityAlertRadius == proximityAlertRadius
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + location.hashCode()
        hashCode = 31 * hashCode + livePeriod.hashCode()
        hashCode = 31 * hashCode + heading.hashCode()
        hashCode = 31 * hashCode + proximityAlertRadius.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("LiveLocation")
            append("(")
            append("location=")
            append(location)
            append(", ")
            append("livePeriod=")
            append(livePeriod)
            append(", ")
            append("heading=")
            append(heading)
            append(", ")
            append("proximityAlertRadius=")
            append(proximityAlertRadius)
            append(")")
        }
    }
}
