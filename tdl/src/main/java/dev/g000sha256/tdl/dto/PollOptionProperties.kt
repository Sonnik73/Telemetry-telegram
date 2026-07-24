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
 * Contains properties of a poll option and describes actions that can be done with the option right now.
 *
 * @property canBeDeleted True, if the option can be deleted using deletePollOption.
 * @property canBeReplied True, if the poll option can be replied in the same chat and forum topic using inputMessageReplyToMessage.
 * @property canBeRepliedInAnotherChat True, if the poll option can be replied in another chat or forum topic using inputMessageReplyToExternalMessage.
 * @property canGetLink True, if a link can be generated for the poll option using getMessageLink.
 */
public class PollOptionProperties public constructor(
    public val canBeDeleted: Boolean,
    public val canBeReplied: Boolean,
    public val canBeRepliedInAnotherChat: Boolean,
    public val canGetLink: Boolean,
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
        other as PollOptionProperties
        if (other.canBeDeleted != canBeDeleted) {
            return false
        }
        if (other.canBeReplied != canBeReplied) {
            return false
        }
        if (other.canBeRepliedInAnotherChat != canBeRepliedInAnotherChat) {
            return false
        }
        return other.canGetLink == canGetLink
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + canBeDeleted.hashCode()
        hashCode = 31 * hashCode + canBeReplied.hashCode()
        hashCode = 31 * hashCode + canBeRepliedInAnotherChat.hashCode()
        hashCode = 31 * hashCode + canGetLink.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("PollOptionProperties")
            append("(")
            append("canBeDeleted=")
            append(canBeDeleted)
            append(", ")
            append("canBeReplied=")
            append(canBeReplied)
            append(", ")
            append("canBeRepliedInAnotherChat=")
            append(canBeRepliedInAnotherChat)
            append(", ")
            append("canGetLink=")
            append(canGetLink)
            append(")")
        }
    }
}
