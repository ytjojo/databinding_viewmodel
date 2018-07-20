/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.databinding.annotationprocessor;

import android.databinding.tool.CompilerChef;
import android.databinding.tool.Context;
import android.databinding.tool.DataBindingCompilerArgs;
import android.databinding.tool.processing.Scope;
import android.databinding.tool.processing.ScopedException;
import android.databinding.tool.util.L;
import android.databinding.tool.util.Preconditions;
import android.databinding.tool.writer.AnnotationJavaFileWriter;
import android.databinding.tool.writer.BRWriter;
import android.databinding.tool.writer.JavaFileWriter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.xml.bind.JAXBException;

@SupportedAnnotationTypes({
        "android.databinding.BindingAdapter",
        "android.databinding.InverseBindingMethods",
        "android.databinding.InverseBindingAdapter",
        "android.databinding.InverseMethod",
        "android.databinding.Untaggable",
        "android.databinding.BindingMethods",
        "android.databinding.BindingConversion",
        "android.databinding.BindingBuildInfo"}
)
/**
 * Parent annotation processor that dispatches sub steps to ensure execution order.
 * Use initProcessingSteps to add a new step.
 */
public class ProcessDataBinding extends AbstractProcessor {
    private List<ProcessingStep> mProcessingSteps;
    private DataBindingCompilerArgs mCompilerArgs;
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            return doProcess(roundEnv);
        } finally {
            if (roundEnv.processingOver()) {
                Context.fullClear(processingEnv);
            }
        }
    }

    private boolean doProcess(RoundEnvironment roundEnv) {
        if (mProcessingSteps == null) {
            readArguments();
            initProcessingSteps();
        }
        if (mCompilerArgs == null) {
            return false;
        }
        if (mCompilerArgs.isTestVariant() && !mCompilerArgs.isEnabledForTests() &&
                !mCompilerArgs.isLibrary()) {
            L.d("data binding processor is invoked but not enabled, skipping...");
            return false;
        }
        boolean done = true;
        Context.init(processingEnv, mCompilerArgs);
        for (ProcessingStep step : mProcessingSteps) {
            try {
                done = step.runStep(roundEnv, processingEnv, mCompilerArgs) && done;
            } catch (JAXBException e) {
                L.e(e, "Exception while handling step %s", step);
            }
        }
        if (roundEnv.processingOver()) {
            for (ProcessingStep step : mProcessingSteps) {
                step.onProcessingOver(roundEnv, processingEnv, mCompilerArgs);
            }
        }
        if (roundEnv.processingOver()) {
            Scope.assertNoError();
        }
        return done;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private void initProcessingSteps() {
        final ProcessBindable processBindable = new ProcessBindable();
        mProcessingSteps = Arrays.asList(
                new ProcessMethodAdapters(),
                new ProcessExpressions(),
                processBindable
        );
        Callback dataBinderWriterCallback = new Callback() {
            CompilerChef mChef;
            BRWriter mBRWriter;
            List<String> mModulePackages;
            boolean mWrittenMapper = false;

            @Override
            public void onChefReady(CompilerChef chef) {
                Preconditions.checkNull(mChef, "Cannot set compiler chef twice");
                chef.addBRVariables(processBindable);
                mChef = chef;
                considerWritingMapper();
            }

            private void considerWritingMapper() {
                if (mWrittenMapper || mChef == null || mBRWriter == null) {
                    return;
                }
                boolean justLibrary =
                        mCompilerArgs.artifactType() == DataBindingCompilerArgs.Type.LIBRARY &&
                                !mCompilerArgs.isTestVariant();
                if (justLibrary && !mCompilerArgs.isEnableV2()) {
                    return;
                }
                mWrittenMapper = true;
                mChef.writeDataBinderMapper(mCompilerArgs, mBRWriter, mModulePackages);
            }

            @Override
            public void onBrWriterReady(BRWriter brWriter, List<String> brPackages) {
                Preconditions.checkNull(mBRWriter, "Cannot set br writer twice");
                mBRWriter = brWriter;
                mModulePackages = brPackages;
                considerWritingMapper();
            }
        };
        AnnotationJavaFileWriter javaFileWriter = new AnnotationJavaFileWriter(processingEnv);
        for (ProcessingStep step : mProcessingSteps) {
            step.mJavaFileWriter = javaFileWriter;
            step.mCallback = dataBinderWriterCallback;
        }
    }

    /**
     * use this instead of init method so that we won't become a problem when data binding happens
     * to be in annotation processor classpath by chance
     */
    private synchronized void readArguments() {
        try {
            mCompilerArgs = DataBindingCompilerArgs
                    .readFromOptions(processingEnv.getOptions());
            L.setDebugLog(mCompilerArgs.enableDebugLogs());
            ScopedException.encodeOutput(mCompilerArgs.shouldPrintEncodedErrorLogs());
        } catch (Throwable t) {
            String allParam = processingEnv.getOptions().entrySet().stream().map(
                    (entry) -> entry.getKey() + " : " + entry.getValue())
                    .collect(Collectors.joining("\n"));
            throw new RuntimeException("Failed to parse data binding compiler options. Params:\n"
                    + allParam, t);
        }
    }

    @Override
    public Set<String> getSupportedOptions() {
        return DataBindingCompilerArgs.ALL_PARAMS;
    }

    /**
     * To ensure execution order and binding build information, we use processing steps.
     */
    public abstract static class ProcessingStep {
        private boolean mDone;
        private JavaFileWriter mJavaFileWriter;
        protected Callback mCallback;

        protected JavaFileWriter getWriter() {
            return mJavaFileWriter;
        }

        private boolean runStep(RoundEnvironment roundEnvironment,
                ProcessingEnvironment processingEnvironment,
                DataBindingCompilerArgs args) throws JAXBException {
            if (mDone) {
                return true;
            }
            mDone = onHandleStep(roundEnvironment, processingEnvironment, args);
            return mDone;
        }

        /**
         * Invoked in each annotation processing step.
         *
         * @return True if it is done and should never be invoked again.
         */
        abstract public boolean onHandleStep(RoundEnvironment roundEnvironment,
                ProcessingEnvironment processingEnvironment,
                DataBindingCompilerArgs args) throws JAXBException;

        /**
         * Invoked when processing is done. A good place to generate the output if the
         * processor requires multiple steps.
         */
        abstract public void onProcessingOver(RoundEnvironment roundEnvironment,
                ProcessingEnvironment processingEnvironment,
                DataBindingCompilerArgs args);
    }

    interface Callback {
        void onChefReady(CompilerChef chef);
        void onBrWriterReady(BRWriter brWriter, List<String> brPackages);
    }
}
