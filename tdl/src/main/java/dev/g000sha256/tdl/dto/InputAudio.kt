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
 * An audio to be sent.
 *
 * @property audio Audio file to be sent.
 * @property albumCoverThumbnail Thumbnail of the cover for the album; pass null to skip thumbnail uploading.
 * @property duration Duration of the audio, in seconds; may be replaced by the server.
 * @property title Title of the audio; 0-64 characters; may be replaced by the server.
 * @property performer Performer of the audio; 0-64 characters, may be replaced by the server.
 */
public class InputAudio public constructor(
    public val audio: InputFile,
    public val albumCoverThumbnail: InputThumbnail?,
    public val duration: Int,
    public val title: String,
    public val performer: String,
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
        other as InputAudio
        if (other.audio != audio) {
            return false
        }
        if (other.albumCoverThumbnail != albumCoverThumbnail) {
            return false
        }
        if (other.duration != duration) {
            return false
        }
        if (other.title != title) {
            return false
        }
        return other.performer == performer
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + audio.hashCode()
        hashCode = 31 * hashCode + albumCoverThumbnail.hashCode()
        hashCode = 31 * hashCode + duration.hashCode()
        hashCode = 31 * hashCode + title.hashCode()
        hashCode = 31 * hashCode + performer.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("InputAudio")
            append("(")
            append("audio=")
            append(audio)
            append(", ")
            append("albumCoverThumbnail=")
            append(albumCoverThumbnail)
            append(", ")
            append("duration=")
            append(duration)
            append(", ")
            append("title=")
            append(title)
            append(", ")
            append("performer=")
            append(performer)
            append(")")
        }
    }
}
