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
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.Long
import kotlin.String

/**
 * A user changed the answer to a poll; for bots only.
 *
 * @property pollId Unique poll identifier.
 * @property voterId Identifier of the message sender that changed the answer to the poll.
 * @property optionIds Unique identifiers of answer options, that were chosen by the user.
 * @property optionPositions 0-based identifiers of answer options, that were chosen by the user.
 */
public class UpdatePollAnswer public constructor(
    public val pollId: Long,
    public val voterId: MessageSender,
    public val optionIds: Array<String>,
    public val optionPositions: IntArray,
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
        other as UpdatePollAnswer
        if (other.pollId != pollId) {
            return false
        }
        if (other.voterId != voterId) {
            return false
        }
        val optionIdsEquals = other.optionIds.contentDeepEquals(optionIds)
        if (!optionIdsEquals) {
            return false
        }
        return other.optionPositions.contentEquals(optionPositions)
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + pollId.hashCode()
        hashCode = 31 * hashCode + voterId.hashCode()
        hashCode = 31 * hashCode + optionIds.contentDeepHashCode()
        hashCode = 31 * hashCode + optionPositions.contentHashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("UpdatePollAnswer")
            append("(")
            append("pollId=")
            append(pollId)
            append(", ")
            append("voterId=")
            append(voterId)
            append(", ")
            append("optionIds=")
            optionIds
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("optionPositions=")
            optionPositions
                .contentToString()
                .also { append(it) }
            append(")")
        }
    }
}
