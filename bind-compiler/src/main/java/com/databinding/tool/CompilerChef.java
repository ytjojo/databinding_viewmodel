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

package com.databinding.tool;

import com.databinding.tool.processing.Scope;
import com.databinding.tool.processing.ScopedException;
import com.databinding.tool.reflection.InjectedClass;
import com.databinding.tool.reflection.ModelAnalyzer;
import com.databinding.tool.store.GenClassInfoLog;
import com.databinding.tool.store.ResourceBundle;
import com.databinding.tool.util.L;
import com.databinding.tool.writer.BRWriter;
import com.databinding.tool.writer.BindingMapperWriter;
import com.databinding.tool.writer.BindingMapperWriterV2;
import com.databinding.tool.writer.JavaFileWriter;
import com.databinding.tool.writer.MergedBindingMapperWriter;

import com.databinding.tool.store.GenClassInfoLog;
import com.databinding.tool.writer.BRWriter;
import com.databinding.tool.writer.BindingMapperWriter;
import com.databinding.tool.writer.BindingMapperWriterV2;
import com.databinding.tool.writer.MergedBindingMapperWriter;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chef class for compiler.
 *
 * Different build systems can initiate a version of this to handle their work
 */
public class CompilerChef {
    private static final String[] VERSION_CODES = {
            "BASE",                 // 1
            "BASE_1_1",             // 2
            "CUPCAKE",              // 3
            "DONUT",                // 4
            "ECLAIR",               // 5
            "ECLAIRE_0_1",          // 6
            "ECLAIR_MR1",           // 7
            "FROYO",                // 8
            "GINGERBREAD",          // 9
            "GINGERBREAD_MR1",      // 10
            "HONEYCOMB",            // 11
            "HONEYCOMB_MR1",        // 12
            "HONEYCOMB_MR2",        // 13
            "ICE_CREAM_SANDWICH",   // 14
            "ICE_CREAM_SANDWICH_MR1",// 15
            "JELLY_BEAN",           // 16
            "JELLY_BEAN_MR1",       // 17
            "JELLY_BEAN_MR2",       // 18
            "KITKAT",               // 19
            "KITKAT_WATCH",         // 20
            "LOLLIPOP",             // 21
            "LOLLIPOP_MR1",         // 22
            "M",                    // 23
    };
    private JavaFileWriter mFileWriter;
    private ResourceBundle mResourceBundle;
    private DataBinder mDataBinder;
    private boolean mEnableV2;

    private CompilerChef() {
    }

    public static CompilerChef createChef(ResourceBundle bundle, JavaFileWriter fileWriter,
            DataBindingCompilerArgs compilerArgs) {
        CompilerChef chef = new CompilerChef();

        chef.mResourceBundle = bundle;
        chef.mFileWriter = fileWriter;
        chef.mResourceBundle.validateMultiResLayouts();
        chef.mEnableV2 = compilerArgs.isEnableV2();
        chef.pushClassesToAnalyzer();
        return chef;
    }

    public ResourceBundle getResourceBundle() {
        return mResourceBundle;
    }

    public void ensureDataBinder() {
        if (mDataBinder == null) {
            mDataBinder = new DataBinder(mResourceBundle, mEnableV2);
            mDataBinder.setFileWriter(mFileWriter);
        }
    }

    public boolean hasAnythingToGenerate() {
        L.d("checking if we have anything to generate. bundle size: %s",
                mResourceBundle == null ? -1 : mResourceBundle.getLayoutBundles().size());
        return mResourceBundle != null && mResourceBundle.getLayoutBundles().size() > 0;
    }

    /**
     * Injects ViewDataBinding subclasses to the ModelAnalyzer so that they can be
     * analyzed prior to creation. This is useful for resolving variable setters and
     * View fields during compilation.
     */
    private void pushClassesToAnalyzer() {
        ModelAnalyzer analyzer = ModelAnalyzer.getInstance();
        for (String layoutName : mResourceBundle.getLayoutBundles().keySet()) {
            final HashSet<String> variables = new HashSet();
            final HashSet<String> fields = new HashSet();

            List<ResourceBundle.LayoutFileBundle> bundles =
                    mResourceBundle.getLayoutBundles().get(layoutName);
            final String className = bundles.get(0).getBindingClassPackage() + "."
                    + bundles.get(0).getBindingClassName();
            // inject base class
            InjectedClass bindingClass =
                    new InjectedClass(className, ModelAnalyzer.VIEW_DATA_BINDING);
            analyzer.injectClass(bindingClass);

            for (ResourceBundle.LayoutFileBundle layoutFileBundle : bundles) {
                final HashMap<String, String> imports = new HashMap<String, String>();
                for (ResourceBundle.NameTypeLocation imp : layoutFileBundle.getImports()) {
                    imports.put(imp.name, imp.type);
                }

                for (ResourceBundle.VariableDeclaration variable :
                        layoutFileBundle.getVariables()) {
                    if (variables.add(variable.name)) {
                        bindingClass.addVariable(variable.name, variable.type, imports);
                    }
                }

                for (ResourceBundle.BindingTargetBundle bindingTargetBundle :
                        layoutFileBundle.getBindingTargetBundles()) {
                    if (bindingTargetBundle.getId() != null) {
                        String fieldName = bindingTargetBundle.getId();
                        if (fields.add(fieldName)) {
                            String fieldType = bindingTargetBundle.getInterfaceType();
                            bindingClass.addField(fieldName, fieldType);
                        }
                    }
                }
                // inject implementation
                if (mEnableV2 || bundles.size() > 1) {
                    // Add the implementation class
                    final String implName =
                            layoutFileBundle.getBindingClassPackage() + "."
                            + layoutFileBundle.createImplClassNameWithConfig();
                    analyzer.injectClass(new InjectedClass(implName, className));
                }
            }
        }
    }

    public void writeDataBinderMapper(DataBindingCompilerArgs compilerArgs, BRWriter brWriter,
            List<String> modulePackages) {
        if (compilerArgs.isEnableV2()) {
            final boolean generateMapper;
            if (compilerArgs.isApp()) {
                // generate mapper for apps only if it is not test or enabled for tests.
                generateMapper = !compilerArgs.isTestVariant() || compilerArgs.isEnabledForTests();
            } else {
                // always generate mapper for libs
                generateMapper = true;
            }
            if (generateMapper) {
                writeMapperForModule(compilerArgs, brWriter);
            }

            // merged mapper is the one generated for the whole app that includes the mappers
            // generated for individual modules.
            final boolean generateMergedMapper;
            if (compilerArgs.isApp()) {
                generateMergedMapper = !compilerArgs.isTestVariant();
            } else {
                generateMergedMapper = compilerArgs.isTestVariant();
            }
            if (generateMergedMapper) {
                writeMergedMapper(compilerArgs, modulePackages);
            }
        } else {
            final String pkg = "android.databinding";
            final String mapperName = "DataBinderMapperImpl";

            ensureDataBinder();
            BindingMapperWriter dbr = new BindingMapperWriter(pkg, mapperName,
                    mDataBinder.getLayoutBinders(), compilerArgs);
            mFileWriter.writeToFile(pkg + "." + dbr.getClassName(),
                    dbr.write(brWriter));
        }
    }

    /**
     * Writes the mapper android.databinding.DataBinderMapperImpl which is a merged mapper
     * that includes all mappers from dependencies.
     */
    private void writeMergedMapper(DataBindingCompilerArgs compilerArgs,
            List<String> modulePackages) {
        StringBuilder sb = new StringBuilder();
        MergedBindingMapperWriter mergedBindingMapperWriter =
                new MergedBindingMapperWriter(modulePackages, compilerArgs);
        TypeSpec mergedMapperSpec = mergedBindingMapperWriter.write();
        try {
            JavaFile.builder(mergedBindingMapperWriter.getPkg(), mergedMapperSpec)
                    .build().writeTo(sb);
            mFileWriter.writeToFile(mergedBindingMapperWriter.getQualifiedName(),
                    sb.toString());
        } catch (IOException e) {
            Scope.defer(new ScopedException("cannot generate merged mapper class", e));
        }
    }

    /**
     * Generates a mapper that knows only about the bindings in this module (excl dependencies).
     */
    private void writeMapperForModule(DataBindingCompilerArgs compilerArgs, BRWriter brWriter) {
        GenClassInfoLog infoLog;
        try {
            infoLog = ResourceBundle.loadClassInfoFromFolder(
                    new File(compilerArgs.getClassLogDir()));
        } catch (IOException e) {
            Scope.defer(new ScopedException("Cannot read class info log"));
            infoLog = new GenClassInfoLog();
        }
        GenClassInfoLog infoLogInThisModule = infoLog
                .createPackageInfoLog(compilerArgs.getModulePackage());
        BindingMapperWriterV2 v2 = new BindingMapperWriterV2(
                infoLogInThisModule,
                compilerArgs);
        TypeSpec spec = v2.write(brWriter);
        StringBuilder sb = new StringBuilder();
        try {
            JavaFile.builder(v2.getPkg(), spec).build().writeTo(sb);
            mFileWriter.writeToFile(v2.getQualifiedName(), sb.toString());
        } catch (IOException e) {
            Scope.defer(new ScopedException("cannot generate mapper class", e));
        }
    }

    /**
     * Adds variables to list of Bindables.
     */
    public void addBRVariables(BindableHolder bindables) {
        ensureDataBinder();
        for (LayoutBinder layoutBinder : mDataBinder.mLayoutBinders) {
            for (String variableName : layoutBinder.getUserDefinedVariables().keySet()) {
                bindables.addVariable(variableName, layoutBinder.getClassName());
            }
        }
    }

    public void sealModels() {
        ensureDataBinder();
        mDataBinder.sealModels();
    }

    public void writeViewBinderInterfaces(boolean isLibrary) {
        ensureDataBinder();
        mDataBinder.writerBaseClasses(isLibrary);
    }

    public void writeViewBinders(int minSdk) {
        ensureDataBinder();
        mDataBinder.writeBinders(minSdk);
    }

    public void writeComponent() {
        ensureDataBinder();
        mDataBinder.writeComponent();
    }

    public Set<String> getClassesToBeStripped() {
        ensureDataBinder();
        return mDataBinder.getClassesToBeStripped();
    }

    public interface BindableHolder {
        void addVariable(String variableName, String containingClassName);
    }
}
