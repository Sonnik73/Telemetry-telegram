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
import kotlin.IntArray
import kotlin.String

/**
 * A poll in quiz mode, which has predefined correct answers.
 *
 * @property correctOptionIds Increasing list of 0-based identifiers of the correct answer options; empty for a yet unanswered poll.
 * @property explanation Text that is shown when the user chooses an incorrect answer or taps on the lamp icon; empty for a yet unanswered poll.
 * @property explanationMedia Media that is shown when the user chooses an incorrect answer or taps on the lamp icon; may be null if none or the poll is unanswered yet. If present, currently, can be only of the types pollMediaAnimation, pollMediaAudio, pollMediaDocument, pollMediaLocation, pollMediaPhoto, pollMediaVenue, or pollMediaVideo.
 */
public class PollTypeQuiz public constructor(
    public val correctOptionIds: IntArray,
    public val explanation: FormattedText,
    public val explanationMedia: PollMedia?,
) : PollType() {
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
        other as PollTypeQuiz
        val correctOptionIdsEquals = other.correctOptionIds.contentEquals(correctOptionIds)
        if (!correctOptionIdsEquals) {
            return false
        }
        if (other.explanation != explanation) {
            return false
        }
        return other.explanationMedia == explanationMedia
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + correctOptionIds.contentHashCode()
        hashCode = 31 * hashCode + explanation.hashCode()
        hashCode = 31 * hashCode + explanationMedia.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("PollTypeQuiz")
            append("(")
            append("correctOptionIds=")
            correctOptionIds
                .contentToString()
                .also { append(it) }
            append(", ")
            append("explanation=")
            append(explanation)
            append(", ")
            append("explanationMedia=")
            append(explanationMedia)
            append(")")
        }
    }
}
