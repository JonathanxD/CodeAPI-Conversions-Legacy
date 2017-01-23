/**
 *      CodeAPI-Conversions - Conversions from JVM Elements to CodeAPI Elements
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2016 JonathanxD <https://github.com/JonathanxD/CodeAPI-Conversions>
 *      Copyright (c) contributors
 *
 *
 *      Permission is hereby granted, free of charge, to any person obtaining a copy
 *      of this software and associated documentation files (the "Software"), to deal
 *      in the Software without restriction, including without limitation the rights
 *      to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *      copies of the Software, and to permit persons to whom the Software is
 *      furnished to do so, subject to the following conditions:
 *
 *      The above copyright notice and this permission notice shall be included in
 *      all copies or substantial portions of the Software.
 *
 *      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *      IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *      FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *      AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *      LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *      OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *      THE SOFTWARE.
 */
package com.github.jonathanxd.codeapi.conversions.test

import com.github.jonathanxd.codeapi.CodeAPI
import com.github.jonathanxd.codeapi.MutableCodeSource
import com.github.jonathanxd.codeapi.base.BodyHolder
import com.github.jonathanxd.codeapi.common.CodeModifier
import com.github.jonathanxd.codeapi.conversions.extend
import org.junit.Test
import java.util.*

class TestConversion {

    @Test
    fun test() {
        val declaration = CodeAPI.aClassBuilder()
                .withModifiers(EnumSet.of(CodeModifier.PUBLIC))
                .withQualifiedName("com.Test")
                .withBody(MutableCodeSource())
                .build()
                .extend(TestX::class.java)

        fun print(any: Any, dash: String = "") {
            println("$dash$any")

            if (any is BodyHolder) {
                any.body.forEach { print(it, dash + "  ") }
            }
        }

        declaration.body.forEach { print(it, "") }
    }

}