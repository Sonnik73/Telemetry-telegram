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
 * Contains an example of text composition style usage.
 *
 * @property sourceText Source text.
 * @property resultText The text after the style was applied to the source text.
 */
public class TextCompositionStyleExample public constructor(
    public val sourceText: FormattedText,
    public val resultText: FormattedText,
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
        other as TextCompositionStyleExample
        if (other.sourceText != sourceText) {
            return false
        }
        return other.resultText == resultText
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + sourceText.hashCode()
        hashCode = 31 * hashCode + resultText.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("TextCompositionStyleExample")
            append("(")
            append("sourceText=")
            append(sourceText)
            append(", ")
            append("resultText=")
            append(resultText)
            append(")")
        }
    }
}
