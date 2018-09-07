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

package com.databinding.tool.util

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


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


data class VersionedResult<T>(val version: Int, val result: T)

fun <K, T> lazyProp(initializer: (k: K) -> T): ReadOnlyProperty<K, T> = LazyExt(initializer)

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
    if(name.contains(':')) {
        return name.split(':')[1]
    } else {
        return name
    }
}

public fun String.exprToAndroidResourceValue():String{
    val expr = this.trim();
    val index = expr.indexOf('.')
    if(index >=0){
        return expr.substring(index+1)
    }else{
        return expr
    }

}
