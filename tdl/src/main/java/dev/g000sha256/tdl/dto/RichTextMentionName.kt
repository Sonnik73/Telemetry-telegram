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
 * A rich text that serves as a mention of a user.
 *
 * @property text Text.
 * @property userId Identifier of the mentioned user.
 */
public class RichTextMentionName public constructor(
    public val text: RichText,
    public val userId: Long,
) : RichText() {
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
        other as RichTextMentionName
        if (other.text != text) {
            return false
        }
        return other.userId == userId
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + text.hashCode()
        hashCode = 31 * hashCode + userId.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("RichTextMentionName")
            append("(")
            append("text=")
            append(text)
            append(", ")
            append("userId=")
            append(userId)
            append(")")
        }
    }
}
