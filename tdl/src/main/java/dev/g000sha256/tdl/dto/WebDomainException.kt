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
import kotlin.Long
import kotlin.String

/**
 * Describes an exception for built-in browser usage.
 *
 * @property url URL for which the exception is done.
 * @property domain Domain of the URL. All URLs on the domain and subdomains of the domain are subject to the exception.
 * @property title Title of the website.
 * @property faviconCustomEmojiId Identifier of the custom emoji with favicon of the website; may be 0 if unknown, in which case the first letter of the domain must be used.
 */
public class WebDomainException public constructor(
    public val url: String,
    public val domain: String,
    public val title: String,
    public val faviconCustomEmojiId: Long,
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
        other as WebDomainException
        if (other.url != url) {
            return false
        }
        if (other.domain != domain) {
            return false
        }
        if (other.title != title) {
            return false
        }
        return other.faviconCustomEmojiId == faviconCustomEmojiId
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + url.hashCode()
        hashCode = 31 * hashCode + domain.hashCode()
        hashCode = 31 * hashCode + title.hashCode()
        hashCode = 31 * hashCode + faviconCustomEmojiId.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("WebDomainException")
            append("(")
            append("url=")
            append(url)
            append(", ")
            append("domain=")
            append(domain)
            append(", ")
            append("title=")
            append(title)
            append(", ")
            append("faviconCustomEmojiId=")
            append(faviconCustomEmojiId)
            append(")")
        }
    }
}
