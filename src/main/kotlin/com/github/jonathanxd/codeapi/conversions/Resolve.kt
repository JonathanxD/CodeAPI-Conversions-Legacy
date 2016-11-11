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

import com.github.jonathanxd.codeapi.helper.Helper
import com.github.jonathanxd.codeapi.impl.CodeField
import com.github.jonathanxd.codeapi.impl.CodeMethod
import com.github.jonathanxd.codeapi.interfaces.TypeDeclaration
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Searches the [codeMethod] in this [Class].
 */
fun <T : Any> Class<T>.find(codeMethod: CodeMethod): Method? {
    val filter = filter@ { it: Method ->
        val name = it.name
        val returnType = it.returnType.codeType
        val parameterTypes = it.parameterTypes.map { it.codeType }

        return@filter name == codeMethod.name
                && returnType.`is`(codeMethod.returnType.get())
                && parameterTypes.isEqual(codeMethod.parameters)
    }

    return this.declaredMethods.first(filter) ?: this.methods.first(filter)

}

/**
 * Searches the [codeField] in this [Class].
 */
fun <T : Any> Class<T>.find(codeField: CodeField): Field? {
    val filter = filter@ { it: Field ->
        val name = it.name
        val type = it.type.codeType

        return@filter name == codeField.name
                && type.`is`(codeField.variableType)
    }

    return this.declaredFields.first(filter) ?: this.fields.first(filter)

}

/**
 * Searches the [typeDeclaration] in this [Class].
 */
fun <T : Any> Class<T>.find(typeDeclaration: TypeDeclaration): Class<*>? {
    val filter = filter@ { it: Class<*> ->
        val type = Helper.getJavaType(it)

        return@filter type.`is`(typeDeclaration)
    }

    return this.declaredClasses.first(filter) ?: this.classes.first(filter)

}