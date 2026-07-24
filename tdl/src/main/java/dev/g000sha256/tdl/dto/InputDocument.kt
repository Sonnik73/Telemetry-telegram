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
 * A document (general file) to be sent.
 *
 * @property document File to be sent.
 * @property thumbnail Document thumbnail; pass null to skip thumbnail uploading.
 * @property disableContentTypeDetection Pass true to disable automatic file type detection and send the document as a file. Always true for files sent to secret chats.
 */
public class InputDocument public constructor(
    public val document: InputFile,
    public val thumbnail: InputThumbnail?,
    public val disableContentTypeDetection: Boolean,
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
        other as InputDocument
        if (other.document != document) {
            return false
        }
        if (other.thumbnail != thumbnail) {
            return false
        }
        return other.disableContentTypeDetection == disableContentTypeDetection
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + document.hashCode()
        hashCode = 31 * hashCode + thumbnail.hashCode()
        hashCode = 31 * hashCode + disableContentTypeDetection.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("InputDocument")
            append("(")
            append("document=")
            append(document)
            append(", ")
            append("thumbnail=")
            append(thumbnail)
            append(", ")
            append("disableContentTypeDetection=")
            append(disableContentTypeDetection)
            append(")")
        }
    }
}
