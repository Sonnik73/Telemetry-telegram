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
import kotlin.IntArray
import kotlin.String

/**
 * A photo to be sent.
 *
 * @property photo Photo to be sent. The photo must be at most 10 MB in size. The photo's width and height must not exceed 10000 in total. Width and height ratio must be at most 20.
 * @property thumbnail Photo thumbnail; pass null to skip thumbnail uploading. The thumbnail is sent to the other party only in secret chats.
 * @property video Video of the live photo; not supported in secret chats; pass null if the photo isn't a live photo.
 * @property addedStickerFileIds File identifiers of the stickers added to the photo, if applicable.
 * @property width Photo width; may be replaced by the server.
 * @property height Photo height; may be replaced by the server.
 */
public class InputPhoto public constructor(
    public val photo: InputFile,
    public val thumbnail: InputThumbnail?,
    public val video: InputFile?,
    public val addedStickerFileIds: IntArray,
    public val width: Int,
    public val height: Int,
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
        other as InputPhoto
        if (other.photo != photo) {
            return false
        }
        if (other.thumbnail != thumbnail) {
            return false
        }
        if (other.video != video) {
            return false
        }
        val addedStickerFileIdsEquals = other.addedStickerFileIds.contentEquals(addedStickerFileIds)
        if (!addedStickerFileIdsEquals) {
            return false
        }
        if (other.width != width) {
            return false
        }
        return other.height == height
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + photo.hashCode()
        hashCode = 31 * hashCode + thumbnail.hashCode()
        hashCode = 31 * hashCode + video.hashCode()
        hashCode = 31 * hashCode + addedStickerFileIds.contentHashCode()
        hashCode = 31 * hashCode + width.hashCode()
        hashCode = 31 * hashCode + height.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("InputPhoto")
            append("(")
            append("photo=")
            append(photo)
            append(", ")
            append("thumbnail=")
            append(thumbnail)
            append(", ")
            append("video=")
            append(video)
            append(", ")
            append("addedStickerFileIds=")
            addedStickerFileIds
                .contentToString()
                .also { append(it) }
            append(", ")
            append("width=")
            append(width)
            append(", ")
            append("height=")
            append(height)
            append(")")
        }
    }
}
