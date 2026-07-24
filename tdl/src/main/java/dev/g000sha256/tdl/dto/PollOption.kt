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
import kotlin.String

/**
 * Describes one answer option of a poll.
 *
 * @property id Unique identifier of the option in the poll; may be empty if yet unassigned.
 * @property text Option text; 1-100 characters; may contain only custom emoji entities.
 * @property media Option media; may be null if none. If present, currently, can be only of the types pollMediaAnimation, pollMediaLink, pollMediaLocation, pollMediaPhoto, pollMediaSticker, pollMediaVenue, or pollMediaVideo.
 * @property voterCount Number of voters for this option, available only for closed or voted polls, or if the current user is the creator of the poll.
 * @property votePercentage The percentage of votes for this option; 0-100.
 * @property recentVoterIds Identifiers of recent voters for the option, if the poll is non-anonymous and poll results are available.
 * @property isChosen True, if the option was chosen by the user.
 * @property isBeingChosen True, if the option is being chosen by a pending setPollAnswer request.
 * @property author Identifier of the user or chat who added the option; may be null if the option existed from creation of the poll.
 * @property additionDate Point in time (Unix timestamp) when the option was added; 0 if the option existed from creation of the poll.
 */
public class PollOption public constructor(
    public val id: String,
    public val text: FormattedText,
    public val media: PollMedia?,
    public val voterCount: Int,
    public val votePercentage: Int,
    public val recentVoterIds: Array<MessageSender>,
    public val isChosen: Boolean,
    public val isBeingChosen: Boolean,
    public val author: MessageSender?,
    public val additionDate: Int,
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
        other as PollOption
        if (other.id != id) {
            return false
        }
        if (other.text != text) {
            return false
        }
        if (other.media != media) {
            return false
        }
        if (other.voterCount != voterCount) {
            return false
        }
        if (other.votePercentage != votePercentage) {
            return false
        }
        val recentVoterIdsEquals = other.recentVoterIds.contentDeepEquals(recentVoterIds)
        if (!recentVoterIdsEquals) {
            return false
        }
        if (other.isChosen != isChosen) {
            return false
        }
        if (other.isBeingChosen != isBeingChosen) {
            return false
        }
        if (other.author != author) {
            return false
        }
        return other.additionDate == additionDate
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + id.hashCode()
        hashCode = 31 * hashCode + text.hashCode()
        hashCode = 31 * hashCode + media.hashCode()
        hashCode = 31 * hashCode + voterCount.hashCode()
        hashCode = 31 * hashCode + votePercentage.hashCode()
        hashCode = 31 * hashCode + recentVoterIds.contentDeepHashCode()
        hashCode = 31 * hashCode + isChosen.hashCode()
        hashCode = 31 * hashCode + isBeingChosen.hashCode()
        hashCode = 31 * hashCode + author.hashCode()
        hashCode = 31 * hashCode + additionDate.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("PollOption")
            append("(")
            append("id=")
            append(id)
            append(", ")
            append("text=")
            append(text)
            append(", ")
            append("media=")
            append(media)
            append(", ")
            append("voterCount=")
            append(voterCount)
            append(", ")
            append("votePercentage=")
            append(votePercentage)
            append(", ")
            append("recentVoterIds=")
            recentVoterIds
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("isChosen=")
            append(isChosen)
            append(", ")
            append("isBeingChosen=")
            append(isBeingChosen)
            append(", ")
            append("author=")
            append(author)
            append(", ")
            append("additionDate=")
            append(additionDate)
            append(")")
        }
    }
}
