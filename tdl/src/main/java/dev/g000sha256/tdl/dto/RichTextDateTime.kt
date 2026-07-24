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
 * A date and time.
 *
 * @property text Original text.
 * @property unixTime Point in time (Unix timestamp) representing the date and time.
 * @property formattingType Date and time formatting type; may be null if none and the original text must not be changed.
 */
public class RichTextDateTime public constructor(
    public val text: RichText,
    public val unixTime: Int,
    public val formattingType: DateTimeFormattingType?,
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
        other as RichTextDateTime
        if (other.text != text) {
            return false
        }
        if (other.unixTime != unixTime) {
            return false
        }
        return other.formattingType == formattingType
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + text.hashCode()
        hashCode = 31 * hashCode + unixTime.hashCode()
        hashCode = 31 * hashCode + formattingType.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("RichTextDateTime")
            append("(")
            append("text=")
            append(text)
            append(", ")
            append("unixTime=")
            append(unixTime)
            append(", ")
            append("formattingType=")
            append(formattingType)
            append(")")
        }
    }
}
