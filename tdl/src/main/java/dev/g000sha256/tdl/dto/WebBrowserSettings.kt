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
 * Describes web browser settings.
 *
 * @property openExternalBrowser True, if links are opened in an external browser by default.
 * @property externalExceptions The list of websites which must always be opened in an external browser.
 * @property inAppExceptions The list of websites which must always be opened in the in-app browser.
 * @property displayCloseButton True, if a close button must be shown in the in-app browser; for Android app only.
 */
public class WebBrowserSettings public constructor(
    public val openExternalBrowser: Boolean,
    public val externalExceptions: Array<WebDomainException>,
    public val inAppExceptions: Array<WebDomainException>,
    public val displayCloseButton: Boolean,
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
        other as WebBrowserSettings
        if (other.openExternalBrowser != openExternalBrowser) {
            return false
        }
        val externalExceptionsEquals = other.externalExceptions.contentDeepEquals(externalExceptions)
        if (!externalExceptionsEquals) {
            return false
        }
        val inAppExceptionsEquals = other.inAppExceptions.contentDeepEquals(inAppExceptions)
        if (!inAppExceptionsEquals) {
            return false
        }
        return other.displayCloseButton == displayCloseButton
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + openExternalBrowser.hashCode()
        hashCode = 31 * hashCode + externalExceptions.contentDeepHashCode()
        hashCode = 31 * hashCode + inAppExceptions.contentDeepHashCode()
        hashCode = 31 * hashCode + displayCloseButton.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("WebBrowserSettings")
            append("(")
            append("openExternalBrowser=")
            append(openExternalBrowser)
            append(", ")
            append("externalExceptions=")
            externalExceptions
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("inAppExceptions=")
            inAppExceptions
                .contentDeepToString()
                .also { append(it) }
            append(", ")
            append("displayCloseButton=")
            append(displayCloseButton)
            append(")")
        }
    }
}
