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
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * Describes a message with rich formatting.
 *
 * @property blocks Content of the message.
 * @property isRtl True, if the message must be shown from right to left.
 * @property isFull True, if the object contains the full message. Otherwise, getFullRichMessage must be used to get the full message.
 */
public class RichMessage public constructor(
    public val blocks: Array<PageBlock>,
    public val isRtl: Boolean,
    public val isFull: Boolean,
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
        other as RichMessage
        val blocksEquals = other.blocks.contentDeepEquals(blocks)
        if (!blocksEquals) {
            return false
        }
        if (other.isRtl != isRtl) {
            return false
        }
        return other.isFull == isFull
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + blocks.contentDeepHashCode()
        hashCode = 31 * hashCode + isRtl.hashCode()
        hashCode = 31 * hashCode + isFull.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("RichMessage")
            append("(")
            append("blocks=")
            blocks
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("isRtl=")
            append(isRtl)
            append(", ")
            append("isFull=")
            append(isFull)
            append(")")
        }
    }
}
