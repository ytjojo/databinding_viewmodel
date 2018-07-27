/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.databinding.tool.writer

import com.databinding.tool.ext.L
import com.databinding.tool.ext.N
import com.databinding.tool.ext.T
import com.databinding.tool.ext.toTypeName
import com.databinding.tool.store.GenClassInfoLog
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

class BaseLayoutBinderWriter(val model: BaseLayoutModel) {
    companion object {
        private val VIEW_DATA_BINDING =
                ClassName.get("android.databinding", "ViewDataBinding")
        private val DATA_BINDING_COMPONENT = ClassName.get(
                "android.databinding", "DataBindingComponent")
        private val ANDROID_VIEW = ClassName.get("android.view", "View")
        private val ANDROID_LAYOUT_INFLATOR = ClassName.get("android.view", "LayoutInflater")
        private val ANDROID_VIEW_GROUP = ClassName.get("android.view", "ViewGroup")
        private val DATA_BINDING_UTIL = ClassName.get("android.databinding", "DataBindingUtil")
        private val NULLABLE = ClassName.get("android.support.annotation", "Nullable")
        private val NON_NULL = ClassName.get("android.support.annotation", "NonNull")
        private val BINDABLE = ClassName.get("android.databinding","Bindable")
    }

    private val binderTypeName = ClassName.get(model.bindingClassPackage, model.bindingClassName)

    fun write(): TypeSpec {
        return TypeSpec.classBuilder(model.bindingClassName).apply {
            superclass(VIEW_DATA_BINDING)
            addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
            addFields(createBindingTargetFields())
            addFields(createVariableFields())
            addMethod(createConstructor())
            addMethods(createGettersAndSetters())
            addMethods(createStaticInflaters())
        }.build()
    }

    private fun createStaticInflaters(): List<MethodSpec> {
        val inflaterParam = ParameterSpec.builder(ANDROID_LAYOUT_INFLATOR, "inflater").apply {
            addAnnotation(NON_NULL)
        }.build()
        val viewGroupParam = ParameterSpec.builder(ANDROID_VIEW_GROUP, "root").apply {
            addAnnotation(NULLABLE)
        }.build()
        val viewParam = ParameterSpec.builder(ANDROID_VIEW, "view").apply {
            addAnnotation(NON_NULL)
        }.build()
        val componentParam = ParameterSpec.builder(DATA_BINDING_COMPONENT, "component").apply {
            addAnnotation(NULLABLE)
        }.build()
        val rLayoutFile = "${model.modulePackage}.R.layout.${model.baseFileName}"
        val attachToRootParam = ParameterSpec.builder(TypeName.BOOLEAN, "attachToRoot").
                build()
        return listOf(
                MethodSpec.methodBuilder("inflate").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    addParameter(inflaterParam)
                    addParameter(viewGroupParam)
                    addParameter(attachToRootParam)
                    returns(binderTypeName)
                    addAnnotation(NON_NULL)
                    addStatement("return inflate($N, $N, $N, $T.getDefaultComponent())",
                            inflaterParam, viewGroupParam, attachToRootParam,
                            DATA_BINDING_UTIL)
                }.build(),
                MethodSpec.methodBuilder("inflate").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    addParameter(inflaterParam)
                    addParameter(viewGroupParam)
                    addParameter(attachToRootParam)
                    addParameter(componentParam)
                    returns(binderTypeName)
                    addAnnotation(NON_NULL)
                    addStatement("return $T.<$T>inflate($N, $L, $N, $N, $N)",
                            DATA_BINDING_UTIL, binderTypeName,
                            inflaterParam, rLayoutFile, viewGroupParam, attachToRootParam,
                            componentParam)
                }.build(),

                MethodSpec.methodBuilder("inflate").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    addParameter(inflaterParam)
                    returns(binderTypeName)
                    addAnnotation(NULLABLE)
                    addStatement("return inflate($N, $T.getDefaultComponent())",
                            inflaterParam, DATA_BINDING_UTIL)
                }.build(),

                MethodSpec.methodBuilder("inflate").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    addParameter(inflaterParam)
                    addParameter(componentParam)
                    returns(binderTypeName)
                    addAnnotation(NON_NULL)
                    addStatement("return $T.<$T>inflate($N, $L, null, false, $N)",
                            DATA_BINDING_UTIL, binderTypeName, inflaterParam,
                            rLayoutFile, componentParam)
                }.build(),

                MethodSpec.methodBuilder("bind").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    addParameter(viewParam)
                    returns(binderTypeName)
                    addAnnotation(NON_NULL)
                    addStatement("return bind($N, $T.getDefaultComponent())",
                            viewParam, DATA_BINDING_UTIL)
                }.build(),
                MethodSpec.methodBuilder("bind").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    addParameter(viewParam)
                    addParameter(componentParam)
                    returns(binderTypeName)
                    addAnnotation(NON_NULL)
                    addStatement("return ($T)bind($N, $N, $L)",
                            binderTypeName, componentParam, viewParam, rLayoutFile)
                }.build()
        )
    }

    private fun createGettersAndSetters(): List<MethodSpec> {
        return model.variables.flatMap { variable ->
            val typeName = variable.type.toTypeName(model.importsByAlias)
            listOf(
                    MethodSpec.methodBuilder(model.setterName(variable)).apply {
                        addModifiers(Modifier.PUBLIC)
                        val param = ParameterSpec.builder(typeName, variable.name).apply {
                            if (!typeName.isPrimitive) {
                                addAnnotation(NULLABLE)
                            }
                        }.build()
                        returns(TypeName.VOID)
                        addParameter(param)
                        addModifiers(Modifier.ABSTRACT)
                        addModifiers(Modifier.PUBLIC)
                    }.build(),
                    MethodSpec.methodBuilder(model.getterName(variable)).apply {
                        addModifiers(Modifier.PUBLIC)
                        returns(typeName)
                        if (!typeName.isPrimitive) {
                            addAnnotation(NULLABLE)
                        }
                        addStatement("return $L", model.fieldName(variable))
                    }.build())
        }
    }

    private fun createConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder().apply {
            addModifiers(Modifier.PROTECTED)
            val componentParam = ParameterSpec
                    .builder(DATA_BINDING_COMPONENT, "_bindingComponent")
                    .build()
            val viewParam = ParameterSpec
                    .builder(ANDROID_VIEW, "_root")
                    .build()
            val localFieldCountParam = ParameterSpec
                    .builder(TypeName.INT, "_localFieldCount")
                    .build()
            addParameter(componentParam)
            addParameter(viewParam)
            addParameter(localFieldCountParam)
            // TODO de-dup construtor param names
            model.sortedTargets.filter { it.id != null }
                    .forEach {
                        val fieldType = (it.interfaceType ?: it.fullClassName)
                                .toTypeName(model.importsByAlias)
                        val targetParam = ParameterSpec.builder(fieldType, model.fieldName(it))
                                .build()
                        addParameter(targetParam)
                    }
            addStatement("super($N, $N, $N)", componentParam, viewParam, localFieldCountParam)
            model.sortedTargets.filter { it.id != null }.forEach {
                // todo might change if we start de-duping constructor params
                val fieldName = model.fieldName(it)
                addStatement("this.$L = $L", fieldName, fieldName)
                if (it.isBinder) {
                    addStatement("setContainedBinding(this.$L);", fieldName)
                }
            }
        }.build()
    }

    fun generateClassInfo(): GenClassInfoLog.GenClass {
        return GenClassInfoLog.GenClass(
                qName = binderTypeName.toString(),
                modulePackage = model.modulePackage,
                variables = model.variables.associate {
                    Pair(it.name, it.type.toTypeName(model.importsByAlias).toString())
                },
                implementations = model.generateImplInfo())
    }

    private fun createVariableFields(): List<FieldSpec> {
        return model.variables.map {
            FieldSpec.builder(it.type.toTypeName(model.importsByAlias), model.fieldName(it),
                    Modifier.PROTECTED)
                    .addAnnotation(BINDABLE) // mark them bindable to trigger BR gen
                    .build()
        }
    }

    private fun createBindingTargetFields(): List<FieldSpec> {
        return model.sortedTargets
                .filter { it.id != null }
                .map {
                    val fieldType = (it.interfaceType ?: it.fullClassName)
                            .toTypeName(model.importsByAlias)
                    FieldSpec.builder(fieldType,
                            model.fieldName(it), Modifier.FINAL).apply {
                        if (it.id != null) {
                            addModifiers(Modifier.PUBLIC)
                        } else {
                            addModifiers(Modifier.PRIVATE)
                        }
                        if (model.inEveryLayout(it)) {
                            addAnnotation(NON_NULL)
                        } else {
                            addAnnotation(NULLABLE)
                        }
                    }.build()
                }
    }
}
