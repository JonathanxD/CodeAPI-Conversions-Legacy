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
package com.github.jonathanxd.codeapi.conversions

import com.github.jonathanxd.codeapi.base.TypeDeclaration
import com.github.jonathanxd.codeapi.common.CodeModifier
import com.github.jonathanxd.codeapi.common.CodeParameter
import com.github.jonathanxd.codeapi.util.fromJavaModifiers
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import kotlin.reflect.KParameter
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

@JvmOverloads
fun Method.isAccessibleFrom(typeDeclaration: TypeDeclaration, override: Boolean = false): Boolean {
    val package_ = typeDeclaration.packageName
    val methodPackage = this.declaringClass.`package`?.name
    val modifiers = this.modifiers

    return if (Modifier.isPublic(modifiers)
            || (override && Modifier.isProtected(modifiers)))
        true
    else methodPackage != null && package_ == methodPackage
}

fun fixModifiers(modifiers: Int): EnumSet<CodeModifier> = EnumSet.copyOf(fromJavaModifiers(modifiers).let {
    it.remove(CodeModifier.ABSTRACT)
    return@let it
})

/**
 * Is the [method] valid for implementation
 */
fun isValidImpl(method: Method) = !method.isBridge
        && !method.isSynthetic
        && !Modifier.isNative(method.modifiers)
        && !Modifier.isFinal(method.modifiers)

fun <T : Any> Iterable<T>.isEqual(other: Iterable<*>): Boolean {

    val thisIterator = this.iterator()
    val otherIterator = other.iterator()

    while (thisIterator.hasNext() && otherIterator.hasNext()) {
        if (thisIterator.next() != otherIterator.next())
            return false

    }

    return !thisIterator.hasNext() && !otherIterator.hasNext()
}

val Method.parameterNames: List<String>
    get() = this.kotlinFunction?.valueParameters?.mapIndexed { i, it -> it.name ?: this.parameters[i].name } ?: this.parameters.map { it.name }

val Method.kotlinParameters: List<KParameter>?
    get() = this.kotlinFunction?.valueParameters

val Method.codeParameters: List<CodeParameter>
    get() = this.kotlinFunction?.valueParameters?.map { it.toCodeParameter() } ?: this.parameters.map { it.toCodeParameter() }