/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.databinding.tool

import com.databinding.tool.ext.cleanLazyProps
import com.databinding.tool.reflection.ModelAnalyzer
import com.databinding.tool.reflection.SdkUtil
import com.databinding.tool.reflection.TypeUtil
import com.databinding.tool.reflection.annotation.AnnotationAnalyzer
import com.databinding.tool.reflection.annotation.AnnotationLogger
import com.databinding.tool.store.SetterStore
import com.databinding.tool.util.GenerationalClassUtil
import com.databinding.tool.util.L
import java.io.File
import javax.annotation.processing.ProcessingEnvironment

/**
 * Simple class to hold all singletons so that it is relatively easier to clean them.
 * We cannot easily get rid of singletons w/o a bigger change so this is a middle ground where
 * we can start clearing them from a central location.
 *
 * Singletons are expected to use this to keep their instances.
 */
object Context {
    private val logger : AnnotationLogger = AnnotationLogger()
    @JvmStatic
    fun init(processingEnvironment: ProcessingEnvironment,
             args : DataBindingCompilerArgs) {
        generationalClassUtil = GenerationalClassUtil.create(args)
        modelAnalyzer = AnnotationAnalyzer(processingEnvironment)
        typeUtil = modelAnalyzer!!.createTypeUtil()
        setterStore = SetterStore.create(modelAnalyzer, generationalClassUtil)
        sdkUtil = SdkUtil.create(File(args.sdkDir), args.minApi)
        L.setClient(logger)
    }

    @JvmStatic
    fun initForTests(modelAnayzer: ModelAnalyzer, sdkUtil: SdkUtil) {
        this.modelAnalyzer = modelAnayzer
        this.sdkUtil = sdkUtil
        typeUtil = modelAnalyzer!!.createTypeUtil()
    }

    @JvmStatic
    var modelAnalyzer : ModelAnalyzer? = null
        private set

    @JvmStatic
    var setterStore : SetterStore? = null
        private set

    @JvmStatic
    var generationalClassUtil : GenerationalClassUtil? = null
        private set

    @JvmStatic
    var typeUtil : TypeUtil? = null
        private set

    @JvmStatic
    var sdkUtil : SdkUtil? = null
        private set

    @JvmStatic
    fun fullClear(processingEnvironment: ProcessingEnvironment) {
        logger.flushMessages(processingEnvironment)
        modelAnalyzer = null
        setterStore = null
        generationalClassUtil = null
        typeUtil = null
        sdkUtil = null
        L.setClient(null)
        cleanLazyProps()
    }
}
