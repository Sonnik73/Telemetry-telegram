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
 * A message with information about an added poll option.
 *
 * @property pollMessageId Identifier of the message with the poll; can be an identifier of a deleted message or 0.
 * @property optionId Identifier of the added option in the poll.
 * @property text Text of the option; 1-100 characters; may contain only custom emoji entities.
 */
public class MessagePollOptionAdded public constructor(
    public val pollMessageId: Long,
    public val optionId: String,
    public val text: FormattedText,
) : MessageContent() {
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
        other as MessagePollOptionAdded
        if (other.pollMessageId != pollMessageId) {
            return false
        }
        if (other.optionId != optionId) {
            return false
        }
        return other.text == text
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + pollMessageId.hashCode()
        hashCode = 31 * hashCode + optionId.hashCode()
        hashCode = 31 * hashCode + text.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("MessagePollOptionAdded")
            append("(")
            append("pollMessageId=")
            append(pollMessageId)
            append(", ")
            append("optionId=")
            append(optionId)
            append(", ")
            append("text=")
            append(text)
            append(")")
        }
    }
}
