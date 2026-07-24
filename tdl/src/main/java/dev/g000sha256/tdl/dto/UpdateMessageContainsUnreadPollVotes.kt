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
 * Unread votes were added or removed from a poll message.
 *
 * @property chatId Chat identifier.
 * @property messageId Message identifier.
 * @property containsUnreadPollVotes True, if the message is a poll message with unread votes.
 * @property unreadPollVoteCount The new number of messages with unread poll votes in the chat.
 */
public class UpdateMessageContainsUnreadPollVotes public constructor(
    public val chatId: Long,
    public val messageId: Long,
    public val containsUnreadPollVotes: Boolean,
    public val unreadPollVoteCount: Int,
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
        other as UpdateMessageContainsUnreadPollVotes
        if (other.chatId != chatId) {
            return false
        }
        if (other.messageId != messageId) {
            return false
        }
        if (other.containsUnreadPollVotes != containsUnreadPollVotes) {
            return false
        }
        return other.unreadPollVoteCount == unreadPollVoteCount
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + chatId.hashCode()
        hashCode = 31 * hashCode + messageId.hashCode()
        hashCode = 31 * hashCode + containsUnreadPollVotes.hashCode()
        hashCode = 31 * hashCode + unreadPollVoteCount.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("UpdateMessageContainsUnreadPollVotes")
            append("(")
            append("chatId=")
            append(chatId)
            append(", ")
            append("messageId=")
            append(messageId)
            append(", ")
            append("containsUnreadPollVotes=")
            append(containsUnreadPollVotes)
            append(", ")
            append("unreadPollVoteCount=")
            append(unreadPollVoteCount)
            append(")")
        }
    }
}
