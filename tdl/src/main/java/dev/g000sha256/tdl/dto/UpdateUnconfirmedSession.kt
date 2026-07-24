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
 * The first unconfirmed session has changed.
 *
 * @property session The unconfirmed session; may be null if none.
 * @property unconfirmedSessionCount The total number of unconfirmed sessions.
 */
public class UpdateUnconfirmedSession public constructor(
    public val session: UnconfirmedSession?,
    public val unconfirmedSessionCount: Int,
) : Update() {
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
        other as UpdateUnconfirmedSession
        if (other.session != session) {
            return false
        }
        return other.unconfirmedSessionCount == unconfirmedSessionCount
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + session.hashCode()
        hashCode = 31 * hashCode + unconfirmedSessionCount.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("UpdateUnconfirmedSession")
            append("(")
            append("session=")
            append(session)
            append(", ")
            append("unconfirmedSessionCount=")
            append(unconfirmedSessionCount)
            append(")")
        }
    }
}
