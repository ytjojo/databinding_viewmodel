/*
 * Copyright (C) 2015 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.databinding.tool.ext

import android.databinding.tool.expr.VersionProvider
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun String.stripNonJava() = this.split("[^a-zA-Z0-9]".toRegex()).map { it.trim() }.joinToCamelCaseAsVar()

/**
 * We keep track of these to be cleaned manually at the end of processing cycle.
 * This is really bad but these codes are from a day where javac would be re-created (hence safely
 * static). Now we need to clean them because javac is not re-created anymore between
 * compilations.
 *
 * Eventually, we should move to a better model similar to the UserProperty stuff in IJ
 * source.
 */
private val mappingHashes = CopyOnWriteArrayList<MutableMap<*, *>>()

fun cleanLazyProps() {
    mappingHashes.forEach {
        it.clear()
    }
}

private class LazyExt<K, T>(private val initializer: (k: K) -> T) : ReadOnlyProperty<K, T> {
    private val mapping = hashMapOf<K, T>()
    init {
        mappingHashes.add(mapping)
    }
    override fun getValue(thisRef: K, property: kotlin.reflect.KProperty<*>): T {
        val t = mapping[thisRef]
        if (t != null) {
            return t
        }
        val result = initializer(thisRef)
        mapping.put(thisRef, result)
        return result
    }
}

private class VersionedLazyExt<K, T>(private val initializer: (k: K) -> T) : ReadOnlyProperty<K, T> {
    private val mapping = hashMapOf<K, VersionedResult<T>>()
    init {
        mappingHashes.add(mapping)
    }
    override fun getValue(thisRef: K, property: KProperty<*>): T {
        val t = mapping[thisRef]
        val version = if (thisRef is VersionProvider) thisRef.version else 1
        if (t != null && version == t.version) {
            return t.result
        }
        val result = initializer(thisRef)
        mapping.put(thisRef, VersionedResult(version, result))
        return result
    }
}

data class VersionedResult<T>(val version: Int, val result: T)

fun <K, T> lazyProp(initializer: (k: K) -> T): ReadOnlyProperty<K, T> = LazyExt(initializer)
fun <K, T> versionedLazy(initializer: (k: K) -> T): ReadOnlyProperty<K, T> = VersionedLazyExt(initializer)

public fun Class<*>.toJavaCode(): String {
    if (name.startsWith('[')) {
        val numArray = name.lastIndexOf('[') + 1;
        val componentType: String;
        when (name[numArray]) {
            'Z' -> componentType = "boolean"
            'B' -> componentType = "byte"
            'C' -> componentType = "char"
            'L' -> componentType = name.substring(numArray + 1, name.length - 1).replace('$', '.');
            'D' -> componentType = "double"
            'F' -> componentType = "float"
            'I' -> componentType = "int"
            'J' -> componentType = "long"
            'S' -> componentType = "short"
            else -> componentType = name.substring(numArray)
        }
        val arrayComp = name.substring(0, numArray).replace("[", "[]");
        return componentType + arrayComp;
    } else {
        return name.replace("$", "")
    }
}

public fun String.androidId(): String {
    val name = this.split("/")[1]
    if (name.contains(':')) {
        return name.split(':')[1]
    } else {
        return name
    }
}

public fun String.toCamelCase(): String {
    val split = this.split("_")
    if (split.size == 0) return ""
    if (split.size == 1) return split[0].capitalize()
    return split.joinToCamelCase()
}

public fun String.toCamelCaseAsVar(): String {
    val split = this.split("_")
    if (split.size == 0) return ""
    if (split.size == 1) return split[0]
    return split.joinToCamelCaseAsVar()
}

public fun String.br(): String =
        "BR.${if (this == "") "_all" else this}"

fun String.readableName() = stripNonJava()

fun String.toTypeName(imports: Map<String, String>) : TypeName {
    return this.toTypeName(imports, true)
}

fun String.toTypeName() : TypeName {
    return toTypeName(imports = null, useReplacements = false)
}

private fun String.toTypeName(imports: Map<String, String>?, useReplacements: Boolean) : TypeName {
    if (this.endsWith("[]")) {
        val qType = this.substring(0, this.length - 2).trim().toTypeName(imports, useReplacements)
        return ArrayTypeName.of(qType)
    }
    val genericEnd = this.lastIndexOf(">")
    if (genericEnd >= 0) {
        val genericStart = this.indexOf("<")
        if (genericStart >= 0) {
            val typeParams = this.substring(genericStart + 1, genericEnd).trim()
            val typeParamsQualified = splitTemplateParameters(typeParams).map {
                it.toTypeName(imports, useReplacements)
            }
            val klass = this.substring(0, genericStart).trim().toTypeName(imports, useReplacements)
            return ParameterizedTypeName.get(klass as ClassName,
                    *typeParamsQualified.toTypedArray())
        }
    }
    if (useReplacements) {
        // check for replacements
        val replacement = REPLACEMENTS[this]
        if (replacement != null) {
            return replacement.toTypeName(imports, useReplacements)
        }
    }
    val import = imports?.get(this)
    if (import != null) {
        return ClassName.bestGuess(import)
    }
    return PRIMITIVE_TYPE_NAME_MAP[this] ?: ClassName.bestGuess(this)
}

private fun splitTemplateParameters(templateParameters: String): ArrayList<String> {
    val list = ArrayList<String>()
    var index = 0
    var openCount = 0
    val arg = StringBuilder()
    while (index < templateParameters.length) {
        val c = templateParameters[index]
        if (c == ',' && openCount == 0) {
            list.add(arg.toString())
            arg.delete(0, arg.length)
        } else if (!Character.isWhitespace(c)) {
            arg.append(c)
            if (c == '<') {
                openCount++
            } else if (c == '>') {
                openCount--
            }
        }
        index++
    }
    list.add(arg.toString())
    return list
}

private val REPLACEMENTS = mapOf(
        "android.view.ViewStub" to "android.databinding.ViewStubProxy"
)

private val PRIMITIVE_TYPE_NAME_MAP = mapOf(
        TypeName.VOID.toString() to TypeName.VOID,
        TypeName.BOOLEAN.toString() to TypeName.BOOLEAN,
        TypeName.BYTE.toString() to TypeName.BYTE,
        TypeName.SHORT.toString() to TypeName.SHORT,
        TypeName.INT.toString() to TypeName.INT,
        TypeName.LONG.toString() to TypeName.LONG,
        TypeName.CHAR.toString() to TypeName.CHAR,
        TypeName.FLOAT.toString() to TypeName.FLOAT,
        TypeName.DOUBLE.toString() to TypeName.DOUBLE)
