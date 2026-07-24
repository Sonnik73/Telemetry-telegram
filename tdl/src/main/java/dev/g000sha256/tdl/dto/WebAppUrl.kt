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
 * Contains information about a Web App URL.
 *
 * @property url The Web App URL to open in a web view.
 * @property requireSameOrigin True, if events from the Web App must be accepted only from the same origin as the URL.
 */
public class WebAppUrl public constructor(
    public val url: String,
    public val requireSameOrigin: Boolean,
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
        other as WebAppUrl
        if (other.url != url) {
            return false
        }
        return other.requireSameOrigin == requireSameOrigin
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + url.hashCode()
        hashCode = 31 * hashCode + requireSameOrigin.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("WebAppUrl")
            append("(")
            append("url=")
            append(url)
            append(", ")
            append("requireSameOrigin=")
            append(requireSameOrigin)
            append(")")
        }
    }
}
