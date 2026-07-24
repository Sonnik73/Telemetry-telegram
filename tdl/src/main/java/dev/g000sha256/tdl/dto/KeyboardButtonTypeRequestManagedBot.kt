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
 * A button that requests creation of a managed bot by the current user; available only in private chats. Use the method createBot to complete the request.
 *
 * @property id Unique button identifier.
 * @property suggestedName Suggested name for the bot; may be empty if not specified.
 * @property suggestedUsername Suggested username for the bot; may be empty if not specified.
 */
public class KeyboardButtonTypeRequestManagedBot public constructor(
    public val id: Int,
    public val suggestedName: String,
    public val suggestedUsername: String,
) : KeyboardButtonType() {
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
        other as KeyboardButtonTypeRequestManagedBot
        if (other.id != id) {
            return false
        }
        if (other.suggestedName != suggestedName) {
            return false
        }
        return other.suggestedUsername == suggestedUsername
    }

    override fun hashCode(): Int {
        var hashCode = this::class.hashCode()
        hashCode = 31 * hashCode + id.hashCode()
        hashCode = 31 * hashCode + suggestedName.hashCode()
        hashCode = 31 * hashCode + suggestedUsername.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return buildString {
            append("KeyboardButtonTypeRequestManagedBot")
            append("(")
            append("id=")
            append(id)
            append(", ")
            append("suggestedName=")
            append(suggestedName)
            append(", ")
            append("suggestedUsername=")
            append(suggestedUsername)
            append(")")
        }
    }
}
