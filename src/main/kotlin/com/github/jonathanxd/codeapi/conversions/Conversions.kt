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
import com.github.jonathanxd.codeapi.base.*
import com.github.jonathanxd.codeapi.base.impl.MethodSpecificationImpl
import com.github.jonathanxd.codeapi.builder.ConstructorDeclarationBuilder
import com.github.jonathanxd.codeapi.builder.FieldDeclarationBuilder
import com.github.jonathanxd.codeapi.builder.MethodDeclarationBuilder
import com.github.jonathanxd.codeapi.common.CodeParameter
import com.github.jonathanxd.codeapi.common.InvokeType
import com.github.jonathanxd.codeapi.common.MethodType
import com.github.jonathanxd.codeapi.common.TypeSpec
import com.github.jonathanxd.codeapi.literal.Literal
import com.github.jonathanxd.codeapi.literal.Literals
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.codeapi.util.fromJavaModifiers
import java.lang.reflect.*
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<T>.toClassDeclaration(): ClassDeclaration =
        CodeAPI.aClassBuilder()
                .withModifiers(fromJavaModifiers(this.modifiers))
                .withQualifiedName(this.canonicalName)
                .withSuperClass((this.superclass as Class<Any>).codeType)
                .withImplementations(this.interfaces.map { it.codeType })
                .withBody(MutableCodeSource())
                .build()

fun <T : Any> Class<T>.toInterfaceDeclaration(): InterfaceDeclaration =
        CodeAPI.anInterfaceBuilder()
                .withModifiers(fromJavaModifiers(this.modifiers))
                .withQualifiedName(this.canonicalName)
                .withImplementations(this.interfaces.map { it.codeType })
                .withBody(MutableCodeSource())
                .build()

fun <T : Any> Class<T>.toAnnotationDeclaration(): AnnotationDeclaration =
        CodeAPI.annotationBuilder()
                .withModifiers(fromJavaModifiers(this.modifiers))
                .withQualifiedName(this.canonicalName)
                .withProperties(this.declaredMethods.map { CodeAPI.property(it.returnType.codeType, it.name, it.defaultValue) })
                .withBody(MutableCodeSource())
                .build()


@JvmOverloads
fun <T : Any> Class<T>.toEnumDeclaration(nameProvider: (method: Method, index: Int, parameter: Parameter) -> String = { m, i, p -> m.parameterNames[i] }): EnumDeclaration {

    val abstractMethods = this.methods.filter { Modifier.isAbstract(it.modifiers) }

    val enumEntries = this.declaredFields
            .filter { it.isEnumConstant }
            .map {
                CodeAPI.enumEntry(it.name,
                        if (abstractMethods.isNotEmpty())
                            MutableCodeSource(abstractMethods.map { it.toMethodDeclaration { index, parameter -> nameProvider(it, index, parameter) } })
                        else
                            null
                )
            }

    return CodeAPI.enumBuilder()
            .withModifiers(fromJavaModifiers(this.modifiers))
            .withQualifiedName(this.canonicalName)
            .withImplementations(this.interfaces.map { it.codeType })
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

    val body = declaration.body as MutableCodeSource

    list += declaration

    if (includeSubClasses) {
        for (declaredClass in this.declaredClasses) {
            val extracted = declaredClass.toStructure(
                    includeFields = includeFields,
                    includeMethods = includeMethods,
                    includeSubClasses = includeSubClasses
            )

            list += extracted.first().builder().withOuterClass(declaration).build()

            if (extracted.size > 1)
                list += extracted.subList(1, extracted.size)
        }
    }

    if (includeFields) {
        for (field in this.fields) {
            body.add(field.toFieldDeclaration())
        }
    }

    if (includeMethods) {
        for (method in this.declaredMethods) {
            if (isValidImpl(method)) body.add(method.toMethodDeclaration())
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
    val body = this.body.toMutable()
    val type = klass.codeType

    for (method in klass.methods) {
        if (method.isAccessibleFrom(this, true)
                && isValidImpl(method)) {
            body.add(method.toMethodDeclaration(type))
        }
    }

    var declaration = this.builder().withBody(body).build()


    if (klass.isInterface) {
        val implementer = declaration as ImplementationHolder
        declaration = implementer.builder().withImplementations(implementer.implementations + type).build() as TypeDeclaration
    } else {
        val extender = declaration as SuperClassHolder
        declaration = extender.builder().withSuperClass(type).build() as TypeDeclaration
    }

    return declaration as T
}

// Fields

/**
 * Convert this [Field] structure to [FieldDeclaration].
 *
 * @return [FieldDeclaration] structure from [Field].
 */
fun Field.toFieldDeclaration(): FieldDeclaration =
        FieldDeclarationBuilder.builder()
                .withModifiers(fromJavaModifiers(this.modifiers))
                .withType(this.type.codeType)
                .withName(this.name)
                .build()

/**
 * Create access to this [Field].
 *
 * @param target Target
 * @return [VariableAccess] to this [Field]
 */
fun Field.createAccess(target: CodePart?): FieldAccess =
        CodeAPI.accessField(
                this.declaringClass.codeType,
                target,
                this.type.codeType,
                this.name
        )

/**
 * Create static access to this [Field].
 *
 * @return **Static** [VariableAccess] to this [Field].
 */
fun Field.createStaticAccess(): FieldAccess =
        this.createAccess(null)

// Method

/**
 * Convert this [Method] to [MethodDeclaration].
 *
 * @param nameProvider Provider of parameter names.
 * @return [MethodDeclaration].
 */
@JvmOverloads
fun Method.toMethodDeclaration(nameProvider: (index: Int, parameter: Parameter) -> String = { i, p -> this.parameterNames[i] }): MethodDeclaration =
        MethodDeclarationBuilder.builder()
                .withModifiers(fixModifiers(this.modifiers))
                .withName(this.name)
                .withReturnType(this.returnType.codeType)
                .withParameters(this.parameters.let {
                    it.mapIndexed { i, it ->
                        CodeAPI.parameter(it.type, nameProvider(i, it))
                    }
                })
                .withBody(MutableCodeSource())
                .build()

/**
 * Convert this [Method] structure to [MethodDeclaration] structure invoking the super class method.
 *
 * @param superClass super class to invoke
 * @param nameProvider Provider of parameter names.
 * @return [MethodDeclaration] structure from [Method] invoking super class method.
 */
@JvmOverloads
fun Method.toMethodDeclaration(superClass: CodeType, nameProvider: (index: Int, parameter: Parameter) -> String = { i, p -> this.parameterNames[i] }): MethodDeclaration =
        this.toMethodDeclaration(nameProvider).builder().withBody(
                MutableCodeSource(
                        arrayOf<CodePart>(CodeAPI.returnValue(this.returnType,
                                CodeAPI.invoke(
                                        InvokeType.INVOKE_SPECIAL,
                                        superClass,
                                        CodeAPI.accessThis(),
                                        this.name,
                                        CodeAPI.typeSpec(this.returnType, *this.parameterTypes),
                                        this.codeParameters.map { it.toCodeArgument() }
                                )))
                )
        ).build()

/**
 * Convert [Method] to [MethodSpecification] with [arguments]
 *
 * @return [MethodSpecification] of this [Method].
 */
fun Method.toSpec(): MethodSpecification =
        MethodSpecificationImpl(
                methodName = this.name,
                description = CodeAPI.typeSpec(this.returnType, *this.parameterTypes),
                methodType = MethodType.METHOD
        )

/**
 * Convert [Method] to [TypeSpec]
 *
 * @return [TypeSpec] of this [Method].
 */
fun Method.toTypeSpec(): TypeSpec = CodeAPI.typeSpec(this.returnType, *this.parameterTypes)

/**
 * Create a [MethodInvocation] to this [Method].
 *
 * @param target Target of invocation (null if method is static)
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun Method.createInvocation(target: CodePart?, arguments: List<CodePart>): MethodInvocation =
        CodeAPI.invoke(
                if (target == null) InvokeType.INVOKE_STATIC else InvokeType.get(this.declaringClass.codeType),
                this.declaringClass.codeType,
                target ?: this.declaringClass.codeType,
                this.name,
                this.toTypeSpec(),
                arguments
        )


/**
 * Create a static [MethodInvocation] to [this method][this].
 *
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun Method.createStaticInvocation(arguments: List<CodePart>): MethodInvocation =
        this.createInvocation(null, arguments)

/**
 * Create a [MethodInvocation] to this [Method] with target to **this**
 *
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun Method.createInvocationThis(arguments: List<CodePart>): MethodInvocation =
        this.createInvocation(CodeAPI.accessThis(), arguments)

// Constructor

/**
 * Convert this [Method] to [MethodDeclaration].
 *
 * @param nameProvider Provider of parameter names.
 * @return [MethodDeclaration].
 */
@JvmOverloads
fun <T : Any> Constructor<T>.toConstructorDeclaration(nameProvider: (index: Int, parameter: Parameter) -> String = { i, p -> this.parameterNames[i] }): ConstructorDeclaration =
        ConstructorDeclarationBuilder.builder()
                .withModifiers(fixModifiers(this.modifiers))
                .withParameters(this.parameters.let {
                    it.mapIndexed { i, it ->
                        CodeAPI.parameter(it.type, nameProvider(i, it))
                    }
                })
                .withBody(MutableCodeSource())
                .build()

/**
 * Convert this [Constructor] structure to [ConstructorDeclaration] structure calling super constructor with [arguments].
 *
 * @param arguments Arguments to pass to super constructor.
 * @param nameProvider Provider of parameter names.
 * @return [ConstructorDeclaration] structure from [Constructor] calling super constructor with [arguments].
 */
@JvmOverloads
fun <T : Any> Constructor<T>.toConstructorDeclaration(arguments: List<CodePart>, nameProvider: (index: Int, parameter: Parameter) -> String = { i, p -> this.parameterNames[i] }): ConstructorDeclaration =
        this.toConstructorDeclaration(nameProvider).builder().withBody(
                MutableCodeSource(
                        arrayOf<CodePart>(
                                CodeAPI.invokeSuperConstructor(
                                        this.toTypeSpec(),
                                        arguments
                                )
                        )
                )
        ).build()

/**
 * Convert [Constructor] to [MethodSpecification]
 *
 * @return [MethodSpecification] of this [Constructor].
 */
fun <T : Any> Constructor<T>.toSpec(): MethodSpecification =
        MethodSpecificationImpl(
                methodName = this.name,
                description = CodeAPI.constructorTypeSpec(*this.parameterTypes),
                methodType = MethodType.CONSTRUCTOR
        )

/**
 * Convert [Method] to [TypeSpec]
 *
 * @return [TypeSpec] of this [Method].
 */
fun <T : Any> Constructor<T>.toTypeSpec(): TypeSpec = CodeAPI.constructorTypeSpec(*this.parameterTypes)

/**
 * Create a [MethodInvocation] to this [Method].
 *
 * @param target Target of invocation (null if method is static)
 * @param arguments Arguments to pass to method.
 * @return The invocation.
 */
fun <T : Any> Constructor<T>.createInvocation(arguments: List<CodePart>): MethodInvocation =
        CodeAPI.invokeConstructor(
                this.declaringClass.declaringClass.codeType,
                this.toTypeSpec(),
                arguments
        )


// Parameters And Arguments
fun KParameter.toCodeParameter(): CodeParameter = CodeAPI.parameter(this.type.jvmErasure.codeType, this.name)

fun Parameter.toCodeParameter(): CodeParameter = CodeAPI.parameter(this.type.codeType, this.name)

fun KParameter.toCodeArgument(): CodePart = CodeAPI.accessLocalVariable(this.type.jvmErasure.codeType, this.name)

fun Parameter.toCodeArgument(): CodePart = CodeAPI.accessLocalVariable(this.type.codeType, this.name)

fun CodeParameter.toCodeArgument(): CodePart = CodeAPI.accessLocalVariable(this.type, this.name)

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