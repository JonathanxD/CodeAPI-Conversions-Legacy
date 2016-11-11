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

import com.github.jonathanxd.codeapi.CodeAPI
import com.github.jonathanxd.codeapi.CodePart
import com.github.jonathanxd.codeapi.MutableCodeSource
import com.github.jonathanxd.codeapi.builder.FieldBuilder
import com.github.jonathanxd.codeapi.builder.MethodBuilder
import com.github.jonathanxd.codeapi.common.CodeArgument
import com.github.jonathanxd.codeapi.common.CodeParameter
import com.github.jonathanxd.codeapi.common.InvokeType
import com.github.jonathanxd.codeapi.common.MethodType
import com.github.jonathanxd.codeapi.helper.Helper
import com.github.jonathanxd.codeapi.impl.CodeField
import com.github.jonathanxd.codeapi.impl.CodeMethod
import com.github.jonathanxd.codeapi.impl.MethodSpecImpl
import com.github.jonathanxd.codeapi.interfaces.*
import com.github.jonathanxd.codeapi.literals.Literal
import com.github.jonathanxd.codeapi.literals.Literals
import com.github.jonathanxd.codeapi.types.CodeType
import com.github.jonathanxd.codeapi.types.LoadedCodeType
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter

val <T : Any> Class<T>.codeType: LoadedCodeType<T>
    get() = this.toType()

fun <T : Any> Class<T>.toType(): LoadedCodeType<T> =
        Helper.getJavaType(this)

fun <T : Any> LoadedCodeType<T>.toClass(): Class<T> =
        this.loadedType

fun <T : Any> Class<T>.toClassDeclaration(): ClassDeclaration =
        CodeAPI.aClassBuilder()
                .withModifiers(this.modifiers)
                .withQualifiedName(this.canonicalName)
                .withSuperClass(this.superclass)
                .withImplementations(*this.interfaces)
                .withBody(MutableCodeSource())
                .build()

fun <T : Any> Class<T>.toInterfaceDeclaration(): InterfaceDeclaration =
        CodeAPI.anInterfaceBuilder()
                .withModifiers(this.modifiers)
                .withQualifiedName(this.canonicalName)
                .withImplementations(*this.interfaces)
                .withBody(MutableCodeSource())
                .build()

fun <T : Any> Class<T>.toAnnotationDeclaration(): AnnotationDeclaration =
        CodeAPI.annotationBuilder()
                .withModifiers(this.modifiers)
                .withQualifiedName(this.canonicalName)
                .withProperties(this.declaredMethods.map { CodeAPI.property(it.returnType.codeType, it.name, it.defaultValue) })
                .withBody(MutableCodeSource())
                .build()


fun <T : Any> Class<T>.toEnumDeclaration(): EnumDeclaration {

    val abstractMethods = this.methods.filter { Modifier.isAbstract(it.modifiers) }

    val enumEntries = this.declaredFields
            .filter { it.isEnumConstant }
            .map {
                CodeAPI.enumEntry(it.name,
                        if (abstractMethods.isNotEmpty())
                            MutableCodeSource(abstractMethods.map { it.toCodeMethod() })
                        else
                            null
                )
            }

    return CodeAPI.enumBuilder()
            .withModifiers(this.modifiers)
            .withQualifiedName(this.canonicalName)
            .withImplementations(*this.interfaces)
            .withEntries(enumEntries)
            .withBody(MutableCodeSource())
            .build()
}

fun <T : Enum<T>> Class<T>.toDeclaration() =
        this.toEnumDeclaration()

fun <T : Annotation> Class<T>.toDeclaration() =
        this.toAnnotationDeclaration()

fun <T : Any> Class<T>.toDeclaration() =
        if (this.isInterface)
            this.toInterfaceDeclaration()
        else if (this.isEnum)
            this.toEnumDeclaration()
        else if (this.isAnnotation)
            this.toAnnotationDeclaration()
        else
            this.toClassDeclaration()


/**
 * Convert this [Class] structure to [TypeDeclaration]s (first element is the
 * input class, other elements is inner-classes).
 *
 * @param includeFields True to include fields.
 * @param includeMethods True to include methods.
 * @param includeSubClasses True to include sub classes.
 * @return [TypeDeclaration] structure from [Class].
 */
@JvmOverloads
fun <T : Any> Class<T>.toStructure(includeFields: Boolean = true, includeMethods: Boolean = true, includeSubClasses: Boolean = true): List<TypeDeclaration> {
    val list = mutableListOf<TypeDeclaration>()

    val declaration: TypeDeclaration = this.toDeclaration()

    val body = declaration.body.get() as MutableCodeSource

    list += declaration

    if (includeSubClasses) {
        for (declaredClass in this.declaredClasses) {
            val extracted = declaredClass.toStructure(
                    includeFields = includeFields,
                    includeMethods = includeMethods,
                    includeSubClasses = includeSubClasses
            )

            list += extracted.first().setOuterClass(declaration)

            if (extracted.size > 1)
                list += extracted.subList(1, extracted.size)
        }
    }

    if (includeFields) {
        for (field in this.fields) {
            body.add(field.toCodeField())
        }
    }

    if (includeMethods) {
        for (method in this.declaredMethods) {
            if (isValidImpl(method)) body.add(method.toCodeMethod())
        }
    }

    return list
}


/**
 * Makes the declaration [T] extend the [Class] overriding all public/protected methods
 * and invoking the super method.
 *
 * @param klass Class to extend
 * @return The declaration extending the [klass] and overriding all public/protected methods.
 */
@Suppress("UNCHECKED_CAST")
fun <T : TypeDeclaration> T.extend(klass: Class<*>): T {
    val body = this.body.map { it.toMutable() }.orElse(MutableCodeSource())
    val type = Helper.getJavaType(klass)

    for (method in klass.methods) {
        if (method.isAccessibleFrom(this, true)
                && isValidImpl(method)) {
            body.add(method.toCodeMethod(type))
        }
    }

    var declaration = this.setBody(body)


    if (klass.isInterface) {
        val implementer = declaration as Implementer
        declaration = implementer.setImplementations(implementer.implementations + type) as TypeDeclaration
    } else {
        val extender = declaration as Extender
        declaration = extender.setSuperType(type) as TypeDeclaration
    }

    return declaration as T
}

// Fields

/**
 * Convert this [Field] structure to [CodeField].
 *
 * @return [CodeField] structure from [Field].
 */
fun Field.toCodeField(): CodeField =
        FieldBuilder.builder()
                .withType(this.type.codeType)
                .withModifiers(this.modifiers)
                .withName(this.name)
                .build()

/**
 * Create access to this [Field].
 *
 * @param target Target
 * @return [VariableAccess] to this [Field]
 */
fun Field.createAccess(target: CodePart?): VariableAccess =
        Helper.accessVariable(
                this.declaringClass.codeType,
                target,
                this.name,
                this.type.codeType)

/**
 * Create static access to this [Field].
 *
 * @return **Static** [VariableAccess] to this [Field].
 */
fun Field.createStaticAccess(): VariableAccess =
        this.createAccess(null)

// Method

/**
 * Convert this [Method] to [CodeMethod].
 *
 * @return [CodeMethod].
 */
fun Method.toCodeMethod(): CodeMethod =
        MethodBuilder.builder()
                .withModifiers(fixModifiers(this.modifiers))
                .withName(this.name)
                .withReturnType(this.returnType)
                .withParameters(this.parameters.map { CodeAPI.parameter(it.type, it.name) })
                .withBody(MutableCodeSource())
                .build()

/**
 * Convert this [Method] structure to [CodeMethod] structure invoking the super class method.
 *
 * @param superClass super class to invoke
 * @return [CodeMethod] structure from [Method] invoking super class method.
 */
fun Method.toCodeMethod(superClass: CodeType): CodeMethod =
        this.toCodeMethod().setBody(
                MutableCodeSource(
                        arrayOf(CodeAPI.returnValue(this.returnType, Helper.invoke(
                                InvokeType.INVOKE_SPECIAL,
                                superClass,
                                CodeAPI.accessThis(),
                                MethodSpecImpl(this.name,
                                        CodeAPI.typeSpec(this.returnType, *this.parameterTypes),
                                        this.parameters.map { it.toCodeArgument() },
                                        MethodType.METHOD)
                        )))
                )
        )

/**
 * Convert [Method] to [MethodSpecification] with [arguments]
 *
 * @param arguments Arguments to pass to method
 * @return [MethodSpecification] of this [Method].
 */
fun Method.toSpec(arguments: List<CodeArgument>): MethodSpecification =
        MethodSpecImpl(this.name,
                CodeAPI.typeSpec(this.returnType, *this.parameterTypes),
                arguments
        )

/**
 * Create a [MethodInvocation] to this [Method].
 *
 * @param target Target of invocation (null if method is static)
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun Method.createInvocation(target: CodePart?, arguments: List<CodeArgument>): MethodInvocation =
        Helper.invoke(
                if (target == null) InvokeType.INVOKE_STATIC else InvokeType.get(this.declaringClass.codeType),
                this.declaringClass.codeType,
                target ?: this.declaringClass.codeType,
                this.toSpec(arguments)
        )


/**
 * Create a static [MethodInvocation] to this [method].
 *
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun Method.createStaticInvocation(arguments: List<CodeArgument>): MethodInvocation =
        this.createInvocation(null, arguments)

/**
 * Create a [MethodInvocation] to this [Method] with target to **this**
 *
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun Method.createInvocationThis(arguments: List<CodeArgument>): MethodInvocation =
        this.createInvocation(CodeAPI.accessThis(), arguments)

// Parameters And Arguments
fun Parameter.toCodeParameter(): CodeParameter = CodeAPI.parameter(this.type.codeType, this.name)

fun Parameter.toCodeArgument(): CodeArgument = CodeAPI.argument(CodeAPI.accessLocalVariable(this.type.codeType, this.name), this.type)

fun CodeParameter.toCodeArgument(): CodeArgument = CodeAPI.argument(CodeAPI.accessLocalVariable(this.requiredType, this.name), this.requiredType)

// Any

/**
 * Convert this value to a literal
 */
fun Any.toLiteral(): Literal? {
    return when (this) {
        is Byte -> Literals.BYTE(this)
        is Short -> Literals.SHORT(this)
        is Int -> Literals.INT(this)
        is Boolean -> Literals.BOOLEAN(this)
        is Long -> Literals.LONG(this)
        is Float -> Literals.FLOAT(this)
        is Double -> Literals.DOUBLE(this)
        is Char -> Literals.CHAR(this)
        is String -> Literals.STRING(this)
        is Class<*> -> Literals.CLASS(this)
        else -> null
    }
}