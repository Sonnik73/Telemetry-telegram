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
 * A video.
 *
 * @property video The video description.
 * @property alternativeVideos Alternative qualities of the video.
 * @property storyboards Available storyboards for the video.
 * @property cover Cover of the video; may be null if none.
 * @property startTimestamp Timestamp from which the video playing must start, in seconds.
 */
public class PollMediaVideo public constructor(
    public val video: Video,
    public val alternativeVideos: Array<AlternativeVideo>,
    public val storyboards: Array<VideoStoryboard>,
    public val cover: Photo?,
    public val startTimestamp: Int,
) : PollMedia() {
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
        other as PollMediaVideo
        if (other.video != video) {
            return false
        }
        val alternativeVideosEquals = other.alternativeVideos.contentDeepEquals(alternativeVideos)
        if (!alternativeVideosEquals) {
            return false
        }
        val storyboardsEquals = other.storyboards.contentDeepEquals(storyboards)
        if (!storyboardsEquals) {
            return false
        }
        if (other.cover != cover) {
            return false
        }
        return other.startTimestamp == startTimestamp
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + video.hashCode()
        hashCode = 31 * hashCode + alternativeVideos.contentDeepHashCode()
        hashCode = 31 * hashCode + storyboards.contentDeepHashCode()
        hashCode = 31 * hashCode + cover.hashCode()
        hashCode = 31 * hashCode + startTimestamp.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("PollMediaVideo")
            append("(")
            append("video=")
            append(video)
            append(", ")
            append("alternativeVideos=")
            alternativeVideos
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("storyboards=")
            storyboards
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("cover=")
            append(cover)
            append(", ")
            append("startTimestamp=")
            append(startTimestamp)
            append(")")
        }
    }
}
