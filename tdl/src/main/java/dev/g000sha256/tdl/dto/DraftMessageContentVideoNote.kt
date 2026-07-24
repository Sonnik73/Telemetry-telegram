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
 * A video note message draft.
 *
 * @property filePath Path to the file with the video note.
 * @property duration Duration of the video, in seconds; 0-60.
 * @property length Video width and height; must be positive and not greater than 640.
 * @property selfDestructType Video note self-destruct type; may be null if none; pass null if none; private chats only.
 */
public class DraftMessageContentVideoNote public constructor(
    public val filePath: String,
    public val duration: Int,
    public val length: Int,
    public val selfDestructType: MessageSelfDestructType?,
) : DraftMessageContent() {
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
        other as DraftMessageContentVideoNote
        if (other.filePath != filePath) {
            return false
        }
        if (other.duration != duration) {
            return false
        }
        if (other.length != length) {
            return false
        }
        return other.selfDestructType == selfDestructType
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + filePath.hashCode()
        hashCode = 31 * hashCode + duration.hashCode()
        hashCode = 31 * hashCode + length.hashCode()
        hashCode = 31 * hashCode + selfDestructType.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("DraftMessageContentVideoNote")
            append("(")
            append("filePath=")
            append(filePath)
            append(", ")
            append("duration=")
            append(duration)
            append(", ")
            append("length=")
            append(length)
            append(", ")
            append("selfDestructType=")
            append(selfDestructType)
            append(")")
        }
    }
}
