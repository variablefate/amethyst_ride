/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip01Core.tags.references

import com.vitorpamplona.quartz.nip96FileStorage.HttpUrlFormatter

class ReferenceTag {
    companion object {
        const val TAG_NAME = "r"
        const val TAG_SIZE = 2

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            reference: String,
        ): Boolean = tag.size >= 2 && tag[0] == TAG_NAME && tag[1] == reference

        @JvmStatic
        fun isIn(
            tag: Array<String>,
            references: Set<String>,
        ): Boolean = tag.size >= 2 && tag[0] == TAG_NAME && tag[1] in references

        @JvmStatic
        fun hasReference(tag: Array<String>): Boolean {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return false
            return tag[1].isNotEmpty()
        }

        @JvmStatic
        fun parse(tag: Array<String>): String? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return tag[1]
        }

        @JvmStatic
        fun assemble(url: String) = arrayOf(TAG_NAME, HttpUrlFormatter.normalize(url))

        @JvmStatic
        fun assemble(urls: List<String>): List<Array<String>> = urls.mapTo(HashSet()) { HttpUrlFormatter.normalize(it) }.map { arrayOf(TAG_NAME, it) }
    }
}
