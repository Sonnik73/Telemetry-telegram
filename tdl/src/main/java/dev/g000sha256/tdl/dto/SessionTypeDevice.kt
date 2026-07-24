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
import kotlin.Long
import kotlin.String

/**
 * A regular session from a device.
 *
 * @property sessionId Unique identifier of the session. Use terminateSession to terminate it or confirmSession to confirm it if it isn't confirmed yet.
 */
public class SessionTypeDevice public constructor(
    public val sessionId: Long,
) : SessionType() {
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
        other as SessionTypeDevice
        return other.sessionId == sessionId
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + sessionId.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("SessionTypeDevice")
            append("(")
            append("sessionId=")
            append(sessionId)
            append(")")
        }
    }
}
