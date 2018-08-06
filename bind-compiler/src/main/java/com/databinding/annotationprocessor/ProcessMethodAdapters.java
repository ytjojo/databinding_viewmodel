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
package com.databinding.annotationprocessor;

import android.databinding.BindingAdapter;
import android.databinding.BindingConversion;
import android.databinding.BindingMethod;
import android.databinding.BindingMethods;
import android.databinding.InverseBindingAdapter;
import android.databinding.InverseBindingMethod;
import android.databinding.InverseBindingMethods;
import android.databinding.InverseMethod;
import android.databinding.Untaggable;
import com.databinding.tool.DataBindingCompilerArgs;
import com.databinding.tool.reflection.ModelAnalyzer;
import com.databinding.tool.reflection.annotation.AnnotationTypeUtil;
import com.databinding.tool.store.SetterStore;
import com.databinding.tool.util.L;
import com.databinding.tool.util.LoggedErrorException;
import com.databinding.tool.util.Preconditions;
import com.databinding.tool.util.StringUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class ProcessMethodAdapters extends ProcessDataBinding.ProcessingStep {
    private final static String INVERSE_BINDING_EVENT_ATTR_SUFFIX = "AttrChanged";

    public ProcessMethodAdapters() {
    }

    @Override
    public boolean onHandleStep(RoundEnvironment roundEnv,
            ProcessingEnvironment processingEnvironment, DataBindingCompilerArgs args) {
        L.d("processing adapters");
        final ModelAnalyzer modelAnalyzer = ModelAnalyzer.getInstance();
        Preconditions.checkNotNull(modelAnalyzer, "Model analyzer should be"
                + " initialized first");
        SetterStore store = SetterStore.get();
        clearIncrementalClasses(roundEnv, store);

        addBindingAdapters(roundEnv, processingEnvironment, store);
        addRenamed(roundEnv, store);
        addConversions(roundEnv, store);
        addUntaggable(roundEnv, store);
        addInverseAdapters(roundEnv, processingEnvironment, store);
        addInverseBindingMethods(roundEnv, store);
        addInverseMethods(roundEnv, processingEnvironment, store);

        try {
            try {
                store.write(args.getModulePackage(), processingEnvironment);
            } catch (IOException e) {
                L.e(e, "Could not write BindingAdapter intermediate file.");
            }
        } catch (LoggedErrorException e) {
            // This will be logged later
        }
        return true;
    }

    @Override
    public void onProcessingOver(RoundEnvironment roundEnvironment,
            ProcessingEnvironment processingEnvironment, DataBindingCompilerArgs args) {

    }

    private void addBindingAdapters(RoundEnvironment roundEnv, ProcessingEnvironment
            processingEnv, SetterStore store) {
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, BindingAdapter.class)) {
            try {
                if (element.getKind() != ElementKind.METHOD ||
                        !element.getModifiers().contains(Modifier.PUBLIC)) {
                    L.e(element, "@BindingAdapter on invalid element: %s", element);
                    continue;
                }
                BindingAdapter bindingAdapter = element.getAnnotation(BindingAdapter.class);

                ExecutableElement executableElement = (ExecutableElement) element;
                List<? extends VariableElement> parameters = executableElement.getParameters();
                if (bindingAdapter.value().length == 0) {
                    L.e(element, "@BindingAdapter requires at least one attribute. %s",
                            element);
                    continue;
                }

                final boolean takesComponent = takesComponent(executableElement, processingEnv);
                final int startIndex = 1 + (takesComponent ? 1 : 0);
                final int numAttributes = bindingAdapter.value().length;
                final int numAdditionalArgs = parameters.size() - startIndex;
                if (numAdditionalArgs == (2 * numAttributes)) {
                    // This BindingAdapter takes old and new values. Make sure they are properly ordered
                    Types typeUtils = processingEnv.getTypeUtils();
                    boolean hasParameterError = false;
                    for (int i = startIndex; i < numAttributes + startIndex; i++) {
                        if (!typeUtils.isSameType(parameters.get(i).asType(),
                                parameters.get(i + numAttributes).asType())) {
                            L.e(executableElement,
                                    "BindingAdapter %s: old values should be followed " +
                                            "by new values. Parameter %d must be the same type as parameter "
                                            +
                                            "%d.", executableElement, i + 1, i + numAttributes + 1);
                            hasParameterError = true;
                            break;
                        }
                    }
                    if (hasParameterError) {
                        continue;
                    }
                } else if (numAdditionalArgs != numAttributes) {
                    L.e(element, "@BindingAdapter %s has %d attributes and %d value " +
                                    "parameters. There should be %d or %d value parameters.",
                            executableElement, numAttributes, numAdditionalArgs, numAttributes,
                            numAttributes * 2);
                    continue;
                }
                warnAttributeNamespaces(element, bindingAdapter.value());
                try {
                    if (numAttributes == 1) {
                        final String attribute = bindingAdapter.value()[0];
                        store.addBindingAdapter(processingEnv, attribute, executableElement,
                                takesComponent);
                    } else {
                        store.addBindingAdapter(processingEnv, bindingAdapter.value(),
                                executableElement, takesComponent, bindingAdapter.requireAll());
                    }
                } catch (IllegalArgumentException e) {
                    L.e(element, "@BindingAdapter for duplicate View and parameter type: %s",
                            element);
                }
            } catch (LoggedErrorException e) {
                // This will be logged later
            }
        }
    }

    private static boolean takesComponent(ExecutableElement executableElement,
            ProcessingEnvironment processingEnvironment) {
        List<? extends VariableElement> parameters = executableElement.getParameters();
        Elements elementUtils = processingEnvironment.getElementUtils();
        TypeMirror viewElement = elementUtils.getTypeElement("android.view.View").asType();
        if (parameters.size() < 2) {
            return false; // Validation will fail in the caller
        }
        TypeMirror parameter1 = parameters.get(0).asType();
        Types typeUtils = processingEnvironment.getTypeUtils();
        if (parameter1.getKind() == TypeKind.DECLARED &&
                typeUtils.isAssignable(parameter1, viewElement)) {
            return false; // first parameter is a View
        }
        if (parameters.size() < 3) {
            TypeMirror viewStubProxy = elementUtils.
                    getTypeElement("android.databinding.ViewStubProxy").asType();
            if (!typeUtils.isAssignable(parameter1, viewStubProxy)) {
                L.e(executableElement, "@BindingAdapter %s is applied to a method that has two " +
                        "parameters, the first must be a View type", executableElement);
            }
            return false;
        }
        TypeMirror parameter2 = parameters.get(1).asType();
        if (typeUtils.isAssignable(parameter2, viewElement)) {
            return true; // second parameter is a View
        }
        L.e(executableElement, "@BindingAdapter %s is applied to a method that doesn't take a " +
                "View subclass as the first or second parameter. When a BindingAdapter uses a " +
                "DataBindingComponent, the component parameter is first and the View " +
                "parameter is second, otherwise the View parameter is first.",
                executableElement);
        return false;
    }

    private static void warnAttributeNamespace(Element element, String attribute) {
        if (attribute.contains(":") && !attribute.startsWith("android:")) {
            L.w(element, "Application namespace for attribute %s will be ignored.", attribute);
        }
    }

    private static void warnAttributeNamespaces(Element element, String[] attributes) {
        for (String attribute : attributes) {
            warnAttributeNamespace(element, attribute);
        }
    }

    private void addRenamed(RoundEnvironment roundEnv, SetterStore store) {
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, BindingMethods.class)) {
            BindingMethods bindingMethods = element.getAnnotation(BindingMethods.class);

            for (BindingMethod bindingMethod : bindingMethods.value()) {
                try {
                    final String attribute = bindingMethod.attribute();
                    final String method = bindingMethod.method();
                    warnAttributeNamespace(element, attribute);
                    String type;
                    try {
                        type = bindingMethod.type().getCanonicalName();
                    } catch (MirroredTypeException e) {
                        type = AnnotationTypeUtil.getInstance().toJava(e.getTypeMirror());
                    }
                    store.addRenamedMethod(attribute, type, method, (TypeElement) element);
                } catch (LoggedErrorException e) {
                    // this will be logged later
                }
            }
        }
    }

    private void addConversions(RoundEnvironment roundEnv, SetterStore store) {
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, BindingConversion.class)) {
            try {
                if (element.getKind() != ElementKind.METHOD ||
                        !element.getModifiers().contains(Modifier.STATIC) ||
                        !element.getModifiers().contains(Modifier.PUBLIC)) {
                    L.e(element, "@BindingConversion is only allowed on public static methods %s",
                            element);
                    continue;
                }

                ExecutableElement executableElement = (ExecutableElement) element;
                if (executableElement.getParameters().size() != 1) {
                    L.e(element, "@BindingConversion method should have one parameter %s",
                            element);
                    continue;
                }
                if (executableElement.getReturnType().getKind() == TypeKind.VOID) {
                    L.e(element, "@BindingConversion method must return a value %s", element);
                    continue;
                }
                store.addConversionMethod(executableElement);
            } catch (LoggedErrorException e) {
                // this will be logged later
            }
        }
    }

    private void addInverseAdapters(RoundEnvironment roundEnv,
            ProcessingEnvironment processingEnv, SetterStore store) {
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, InverseBindingAdapter.class)) {
            try {
                if (!element.getModifiers().contains(Modifier.PUBLIC)) {
                    L.e(element, "@InverseBindingAdapter must be associated with a public method");
                    continue;
                }
                ExecutableElement executableElement = (ExecutableElement) element;
                if (executableElement.getReturnType().getKind() == TypeKind.VOID) {
                    L.e(element, "@InverseBindingAdapter must have a non-void return type");
                    continue;
                }
                final InverseBindingAdapter inverseBindingAdapter =
                        executableElement.getAnnotation(InverseBindingAdapter.class);
                final String attribute = inverseBindingAdapter.attribute();
                warnAttributeNamespace(element, attribute);
                final String event = inverseBindingAdapter.event().isEmpty()
                        ? inverseBindingAdapter.attribute() + INVERSE_BINDING_EVENT_ATTR_SUFFIX
                        : inverseBindingAdapter.event();
                warnAttributeNamespace(element, event);
                final boolean takesComponent = takesComponent(executableElement, processingEnv);
                final int expectedArgs = takesComponent ? 2 : 1;
                final int numParameters = executableElement.getParameters().size();
                if (numParameters != expectedArgs) {
                    L.e(element,"@InverseBindingAdapter %s takes %s parameters, but %s "
                            + "parameters were expected", element, numParameters, expectedArgs);
                    continue;
                }
                try {
                    store.addInverseAdapter(processingEnv, attribute, event, executableElement,
                            takesComponent);
                } catch (IllegalArgumentException e) {
                    L.e(element, "@InverseBindingAdapter for duplicate View and parameter "
                                    + "type: %s", element);
                }
            } catch (LoggedErrorException e) {
                // This will be logged later
            }
        }
    }

    private void addInverseBindingMethods(RoundEnvironment roundEnv, SetterStore store) {
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, InverseBindingMethods.class)) {
            InverseBindingMethods bindingMethods =
                    element.getAnnotation(InverseBindingMethods.class);

            for (InverseBindingMethod bindingMethod : bindingMethods.value()) {
                try {
                    final String attribute = bindingMethod.attribute();
                    final String method = bindingMethod.method();
                    final String event = bindingMethod.event().isEmpty()
                            ? bindingMethod.attribute() + INVERSE_BINDING_EVENT_ATTR_SUFFIX
                            : bindingMethod.event();
                    warnAttributeNamespace(element, attribute);
                    warnAttributeNamespace(element, event);
                    String type;
                    try {
                        type = bindingMethod.type().getCanonicalName();
                    } catch (MirroredTypeException e) {
                        type = AnnotationTypeUtil.getInstance().toJava(e.getTypeMirror());
                    }
                    store.addInverseBindingMethod(attribute, event, type, method,
                            (TypeElement) element);
                } catch (LoggedErrorException e) {
                    // This will be logged later
                }
            }
        }
    }

    private void addInverseMethods(RoundEnvironment roundEnv, ProcessingEnvironment processingEnv,
            SetterStore store) {
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, InverseMethod.class)) {
            try {
                if (!element.getModifiers().contains(Modifier.PUBLIC)) {
                    L.e(element, "@InverseMethods must be associated with a public method");
                    continue;
                }
                ExecutableElement executableElement = (ExecutableElement) element;
                if (executableElement.getReturnType().getKind() == TypeKind.VOID) {
                    L.e(element, "@InverseMethods must have a non-void return type");
                    continue;
                }
                final InverseMethod inverseMethod =
                        executableElement.getAnnotation(InverseMethod.class);
                final String target = inverseMethod.value();
                if (!StringUtils.isNotBlank(target)) {
                    L.e(element, "@InverseMethod must supply a value containing the name of the " +
                            "method to call when going from View value to bound value");
                    continue;
                }
                if (executableElement.getParameters().isEmpty()) {
                    L.e(element, "@InverseMethods must have at least one parameter.");
                    continue;
                }
                try {
                    ExecutableElement inverse = findInverseOf(processingEnv, executableElement,
                            inverseMethod.value());
                    store.addInverseMethod(processingEnv, executableElement, inverse);
                } catch (IllegalArgumentException e) {
                    L.e(element, "%s", e.getMessage());
                }
            } catch (LoggedErrorException e) {
                // This will be logged later
            }
        }
    }

    private ExecutableElement findInverseOf(ProcessingEnvironment env, ExecutableElement method,
            String name) throws IllegalArgumentException {
        TypeElement enclosingType = (TypeElement) method.getEnclosingElement();
        List<? extends VariableElement> params = method.getParameters();
        Types typeUtil = env.getTypeUtils();
        for (Element element : env.getElementUtils().getAllMembers(enclosingType)) {
            if (element.getKind() == ElementKind.METHOD) {
                ExecutableElement executableElement = (ExecutableElement) element;
                if (!name.equals(executableElement.getSimpleName().toString())) {
                    continue;
                }

                List<? extends VariableElement> checkParams = executableElement.getParameters();
                boolean allTypesMatch = true;
                // now check the parameters
                for (int i = 0; i < params.size() - 1; i++) {
                    TypeMirror expectedType = typeUtil.erasure(params.get(i).asType());
                    TypeMirror foundType = typeUtil.erasure(checkParams.get(i).asType());
                    if (!typeUtil.isSameType(expectedType, foundType)) {
                        allTypesMatch = false;
                        break;
                    }
                }
                if (allTypesMatch) {
                    TypeMirror expectedType = typeUtil.erasure(method.getReturnType());
                    TypeMirror foundType =
                            typeUtil.erasure(checkParams.get(checkParams.size() - 1).asType());
                    allTypesMatch = typeUtil.isSameType(expectedType, foundType);
                    if (allTypesMatch) {
                        // check return type
                        expectedType =
                                typeUtil.erasure(params.get(params.size() - 1).asType());
                        foundType = typeUtil.erasure(executableElement.getReturnType());
                        if (!typeUtil.isSameType(expectedType, foundType)) {
                            throw new IllegalArgumentException(String.format(
                                    "Declared InverseMethod ('%s') does not have the correct " +
                                            "return type. Expected '%s' but was '%s'",
                                    executableElement, expectedType, foundType));
                        }
                    }
                    if (method.getModifiers().contains(Modifier.STATIC) !=
                            executableElement.getModifiers().contains(Modifier.STATIC)) {
                        throw new IllegalArgumentException(String.format(
                                "'%s' declared instance method is different from its " +
                                        "InverseMethod '%s'. Make them both static or instance " +
                                        "methods.", method, executableElement));
                    }
                    if (!executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                        throw new IllegalArgumentException(String.format(
                                "InverseMethod must be declared public '%s'", executableElement));
                    }
                    return executableElement;
                }
            }
        }
        StringBuilder paramStr = new StringBuilder();
        for (int i = 0; i < params.size() - 1; i++) {
            if (i != 0) {
                paramStr.append(", ");
            }
            paramStr.append(params.get(i).asType());
        }
        if (params.size() != 1) {
            paramStr.append(", ");
        }
        paramStr.append(method.getReturnType());
        String staticStr = method.getModifiers().contains(Modifier.STATIC) ? "static " : "";
        TypeMirror returnType = params.get(params.size() - 1).asType();
        throw new IllegalArgumentException(String.format(
                "Could not find inverse method: public %s%s %s(%s)", staticStr, returnType,
                name, paramStr));
    }

    private void addUntaggable(RoundEnvironment roundEnv, SetterStore store) {
        for (Element element : AnnotationUtil.
                getElementsAnnotatedWith(roundEnv, Untaggable.class)) {
            try {
                Untaggable untaggable = element.getAnnotation(Untaggable.class);
                store.addUntaggableTypes(untaggable.value(), (TypeElement) element);
            } catch (LoggedErrorException e) {
                // This will be logged later
            }
        }
    }

    private void clearIncrementalClasses(RoundEnvironment roundEnv, SetterStore store) {
        HashSet<String> classes = new HashSet<String>();

        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, BindingAdapter.class)) {
            TypeElement containingClass = (TypeElement) element.getEnclosingElement();
            classes.add(containingClass.getQualifiedName().toString());
        }
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, BindingMethods.class)) {
            classes.add(((TypeElement) element).getQualifiedName().toString());
        }
        for (Element element : AnnotationUtil
                .getElementsAnnotatedWith(roundEnv, BindingConversion.class)) {
            classes.add(((TypeElement) element.getEnclosingElement()).getQualifiedName().
                    toString());
        }
        for (Element element : AnnotationUtil.
                getElementsAnnotatedWith(roundEnv, Untaggable.class)) {
            classes.add(((TypeElement) element).getQualifiedName().toString());
        }
        store.clear(classes);
    }
}
