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

package com.databinding.tool.writer

import com.databinding.tool.DataBindingCompilerArgs
import com.databinding.tool.LayoutBinder

class BindingMapperWriter(var pkg : String, var className: String,
                          val layoutBinders : List<LayoutBinder>,
                          val compilerArgs: DataBindingCompilerArgs) {
    private val appClassName : String = className
    private val testClassName = "Test$className"
    private val baseMapperClassName = "android.databinding.DataBinderMapper"
    val generateAsTest = compilerArgs.isTestVariant && compilerArgs.isApp
    val generateTestOverride = !generateAsTest && compilerArgs.isEnabledForTests
    init {
        if (generateAsTest) {
            className = "Test${className}"
        }
    }
    fun write(brWriter : BRWriter) = kcode("") {
        nl("package $pkg;")
        nl("import ${compilerArgs.modulePackage}.BR;")
        val extends = if (generateAsTest) {
            "extends $appClassName"
        } else {
            "extends $baseMapperClassName"
        }
        annotateWithGenerated()
        block("class $className $extends") {
            if (generateTestOverride) {
                nl("static $appClassName mTestOverride;")
                block("static") {
                    block("try") {
                        nl("mTestOverride = ($appClassName)$appClassName.class.getClassLoader().loadClass(\"$pkg.$testClassName\").newInstance();")
                    }
                    block("catch(Throwable ignored)") {
                        nl("// ignore, we are not running in test mode")
                        nl("mTestOverride = null;")
                    }

                }
            }
            nl("")
            block("public $className()") {
            }
            nl("")
            nl("@Override")
            block("public android.databinding.ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, android.view.View view, int layoutId)") {
                block("switch(layoutId)") {
                    layoutBinders.groupBy{it.layoutname }.forEach {
                        val firstVal = it.value[0]
                        tab("case ${firstVal.modulePackage}.R.layout.${firstVal.layoutname}:") {
                            // we should check the tag to decide which layout we need to inflate
                            block("") {
                                tab("final Object tag = view.getTag();")
                                tab("if(tag == null) throw new java.lang.RuntimeException(\"view must have a tag\");")
                                it.value.forEach {
                                    block("if (\"${it.tag}_0\".equals(tag))") {
                                        if (it.isMerge) {
                                            tab("return new ${it.`package`}.${it.implementationName}(bindingComponent, new android.view.View[]{view});")
                                        } else {
                                            tab("return new ${it.`package`}.${it.implementationName}(bindingComponent, view);")
                                        }
                                    }
                                }
                                tab("throw new java.lang.IllegalArgumentException(\"The tag for ${firstVal.layoutname} is invalid. Received: \" + tag);");
                            }
                        }
                    }
                }
                if (generateTestOverride) {
                    block("if(mTestOverride != null)") {
                        nl("return mTestOverride.getDataBinder(bindingComponent, view, layoutId);")
                    }
                }
                nl("return null;")
            }
            nl("@Override")
            block("public android.databinding.ViewDataBinding getDataBinder(android.databinding" +
                    ".DataBindingComponent bindingComponent, android.view.View[] views, int layoutId)") {
                block("switch(layoutId)") {
                    layoutBinders.filter{it.isMerge }.groupBy{it.layoutname }.forEach {
                        val firstVal = it.value[0]
                        block("case ${firstVal.modulePackage}.R.layout.${firstVal.layoutname}:") {
                            if (it.value.size == 1) {
                                tab("return new ${firstVal.`package`}.${firstVal.implementationName}(bindingComponent, views);")
                            } else {
                                // we should check the tag to decide which layout we need to inflate
                                nl("final Object tag = views[0].getTag();")
                                nl("if(tag == null) throw new java.lang.RuntimeException(\"view must have a tag\");")
                                it.value.forEach {
                                    block("if (\"${it.tag}_0\".equals(tag))") {
                                        nl("return new ${it.`package`}.${it.implementationName}(bindingComponent, views);")
                                    }
                                }
                            }
                        }
                    }
                }
                if (generateTestOverride) {
                    block("if(mTestOverride != null)") {
                        nl("return mTestOverride.getDataBinder(bindingComponent, views, layoutId);")
                    }
                }
                nl("return null;")
            }
            nl("@Override")
            block("public int getLayoutId(String tag)") {
                block("if (tag == null)") {
                    nl("return 0;");
                }
                // String.hashCode is well defined in the API so we can rely on it being the same on the device and the host machine
                nl("final int code = tag.hashCode();");
                block("switch(code)") {
                    layoutBinders.groupBy {"${it.tag}_0".hashCode()}.forEach {
                        block("case ${it.key}:") {
                            it.value.forEach {
                                block("if(tag.equals(\"${it.tag}_0\"))") {
                                    nl("return ${it.modulePackage}.R.layout.${it.layoutname};")
                                }
                            }
                            nl("break;")
                        }

                    }
                }
                if (generateTestOverride) {
                    block("if(mTestOverride != null)") {
                        nl("return mTestOverride.getLayoutId(tag);")
                    }
                }
                nl("return 0;")
            }
            nl("@Override")
            block("public String convertBrIdToString(int id)") {
                block("if (id < 0 || id >= InnerBrLookup.sKeys.length)") {
                    if (generateTestOverride) {
                        block("if(mTestOverride != null)") {
                            nl("return mTestOverride.convertBrIdToString(id);")
                        }
                    }
                    nl("return null;")
                }
                nl("return InnerBrLookup.sKeys[id];")
            }

            block("private static class InnerBrLookup") {
                nl("static String[] sKeys = new String[]{") {
                    tab("\"_all\"")
                    brWriter.indexedProps.forEach {
                        tab(",\"${it.value}\"")
                    }
                }.app("};")
            }
        }
    }.generate()
}
