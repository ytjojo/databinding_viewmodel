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

package com.databinding.tool;

import com.databinding.tool.expr.Expr;
import com.databinding.tool.expr.ExprModel;
import com.databinding.tool.expr.LambdaExpr;
import com.databinding.tool.processing.ErrorMessages;
import com.databinding.tool.processing.Scope;
import com.databinding.tool.processing.scopes.LocationScopeProvider;
import com.databinding.tool.reflection.ModelAnalyzer;
import com.databinding.tool.reflection.ModelClass;
import com.databinding.tool.reflection.ModelMethod;
import com.databinding.tool.store.Location;
import com.databinding.tool.store.SetterStore;
import com.databinding.tool.util.L;
import com.databinding.tool.util.Preconditions;
import com.databinding.tool.writer.LayoutBinderWriterKt;

import java.util.List;
import java.util.Map;

public class Binding implements LocationScopeProvider {

    private final String mName;
    private Expr mExpr;
    private final BindingTarget mTarget;
    private SetterStore.BindingSetterCall mSetterCall;
    private boolean mUnwrapObservableFields = true;

    public Binding(BindingTarget target, String name, Expr expr) {
        this(target, name, expr, null);
    }

    public Binding(BindingTarget target, String name, Expr expr, SetterStore.BindingSetterCall setterCall) {
        mTarget = target;
        mName = name;
        mExpr = expr;
        mSetterCall = setterCall;
    }

    @Override
    public List<Location> provideScopeLocation() {
        return mExpr.getLocations();
    }

    public void resolveListeners() {
        final ModelClass listenerParameter = getListenerParameter(mTarget, mName, mExpr.getModel());
        Expr listenerExpr = mExpr.resolveListeners(listenerParameter, null);
        if (listenerExpr != mExpr) {
            listenerExpr.markAsBindingExpression();
            mExpr = listenerExpr;
        }
    }

    public void resolveCallbackParams() {
        if (!(mExpr instanceof LambdaExpr)) {
            return;
        }
        LambdaExpr lambdaExpr = (LambdaExpr) mExpr;
        final ModelClass listener = getListenerParameter(mTarget, mName, mExpr.getModel());
        Preconditions.checkNotNull(listener, ErrorMessages.CANNOT_FIND_SETTER_CALL, mName,
                "lambda", getTarget().getInterfaceType());
        //noinspection ConstantConditions
        List<ModelMethod> abstractMethods = listener.getAbstractMethods();
        int numberOfAbstractMethods = abstractMethods.size();
        if (numberOfAbstractMethods != 1) {
            L.e(ErrorMessages.CANNOT_FIND_ABSTRACT_METHOD, mName, listener.getCanonicalName(),
                    numberOfAbstractMethods, 1);
        }
        final ModelMethod method = abstractMethods.get(0);
        final int argCount = lambdaExpr.getCallbackExprModel().getArgCount();
        if (argCount != 0 && argCount != method.getParameterTypes().length) {
            L.e(ErrorMessages.CALLBACK_ARGUMENT_COUNT_MISMATCH, listener.getCanonicalName(),
                    method.getName(), method.getParameterTypes().length, argCount);
        }
        lambdaExpr.setup(listener, method, mExpr.getModel().obtainCallbackId());
    }

    public void resolveTwoWayExpressions() {
        Expr expr = mExpr.resolveTwoWayExpressions(null);
        if (expr != mExpr) {
            mExpr = expr;
        }
    }

    private SetterStore.BindingSetterCall getSetterCall() {
        if (mSetterCall == null) {
            try {
                Scope.enter(getTarget());
                Scope.enter(this);
                resolveSetterCall();
                if (mSetterCall == null) {
                    L.e(ErrorMessages.CANNOT_FIND_SETTER_CALL, mName, mExpr.getResolvedType(),
                            getTarget().getInterfaceType());
                }
            } finally {
                Scope.exit();
                Scope.exit();
            }
        }
        return mSetterCall;
    }

    private void resolveSetterCall() {
        ModelClass viewType = mTarget.getResolvedType();
        if ("android:visibility".equals(mName) && viewType != null && viewType.isViewDataBinding()) {
            mSetterCall = new IncludeVisibilityCall();
        } else if (viewType != null && viewType.extendsViewStub()) {
            mExpr = mExpr.unwrapObservableField();
            if (isListenerAttribute(mName)) {
                ModelAnalyzer modelAnalyzer = ModelAnalyzer.getInstance();
                ModelClass viewStubProxy = modelAnalyzer.
                        findClass("android.databinding.ViewStubProxy", null);
                mSetterCall = SetterStore.get().getSetterCall(mName,
                        viewStubProxy, mExpr.getResolvedType(), mExpr.getModel().getImports());
            } else if (isViewStubAttribute(mName)) {
                mSetterCall = new ViewStubDirectCall(mName, viewType, mExpr.getResolvedType(),
                        mExpr.getModel().getImports());
            } else {
                mSetterCall = new ViewStubSetterCall(mName);
            }
        } else {
            ModelAnalyzer modelAnalyzer = ModelAnalyzer.getInstance();
            if (mExpr.getResolvedType().getObservableGetterName() != null) {
                // If it is an ObservableField, try with the contents of it first.
                Expr expr = mExpr.unwrapObservableField();
                mSetterCall = SetterStore.get().getSetterCall(mName,
                        viewType, expr.getResolvedType(), mExpr.getModel().getImports());
                if (mSetterCall != null) {
                    mExpr = expr;
                }
            }
            if (mSetterCall == null) {
                // Now try with the value object directly
                mSetterCall = SetterStore.get().getSetterCall(mName,
                        viewType, mExpr.getResolvedType(), mExpr.getModel().getImports());
            }
        }
    }

    /**
     * Similar to getSetterCall, but assumes an Object parameter to find the best matching listener.
     */
    private static ModelClass getListenerParameter(BindingTarget target, String name,
            ExprModel model) {
        ModelClass viewType = target.getResolvedType();
        SetterStore.SetterCall setterCall;
        ModelAnalyzer modelAnalyzer = ModelAnalyzer.getInstance();
        ModelClass objectParameter = modelAnalyzer.findClass(Object.class);
        SetterStore setterStore = SetterStore.get();
        if (viewType != null && viewType.extendsViewStub()) {
            if (isListenerAttribute(name)) {
                ModelClass viewStubProxy = modelAnalyzer.
                        findClass("android.databinding.ViewStubProxy", null);
                setterCall = SetterStore.get().getSetterCall(name,
                        viewStubProxy, objectParameter, model.getImports());
            } else if (isViewStubAttribute(name)) {
                setterCall = null; // view stub attrs are not callbacks
            } else {
                setterCall = new ViewStubSetterCall(name);
            }
        } else {
            setterCall = setterStore.getSetterCall(name, viewType, objectParameter,
                    model.getImports());
        }
        if (setterCall != null) {
            return setterCall.getParameterTypes()[0];
        }
        List<SetterStore.MultiAttributeSetter> setters =
                setterStore.getMultiAttributeSetterCalls(new String[]{name}, viewType,
                new ModelClass[] {modelAnalyzer.findClass(Object.class)});
        if (setters.isEmpty()) {
            return null;
        } else {
            return setters.get(0).getParameterTypes()[0];
        }
    }

    public BindingTarget getTarget() {
        return mTarget;
    }

    public String toJavaCode(String targetViewName, String bindingComponent) {
        final String currentValue = requiresOldValue()
                ? "this." + LayoutBinderWriterKt.getOldValueName(mExpr) : null;
        final String argCode = getExpr().toCode().generate();
        return getSetterCall().toJava(bindingComponent, targetViewName, currentValue, argCode);
    }

    public String getBindingAdapterInstanceClass() {
        return getSetterCall().getBindingAdapterInstanceClass();
    }

    public Expr[] getComponentExpressions() {
        return new Expr[] { mExpr };
    }

    public boolean requiresOldValue() {
        return getSetterCall().requiresOldValue();
    }

    /**
     * The min api level in which this binding should be executed.
     * <p>
     * This should be the minimum value among the dependencies of this binding. For now, we only
     * check the setter.
     */
    public int getMinApi() {
        return getSetterCall().getMinApi();
    }

    public String getName() {
        return mName;
    }

    public final Expr getExpr() {
        return mExpr;
    }

    private static boolean isViewStubAttribute(String name) {
        return ("android:inflatedId".equals(name) ||
                "android:layout".equals(name) ||
                "android:visibility".equals(name) ||
                "android:layoutInflater".equals(name));
    }

    private static boolean isListenerAttribute(String name) {
        return ("android:onInflate".equals(name) ||
                "android:onInflateListener".equals(name));
    }

    public void injectSafeUnboxing(ExprModel exprModel) {
        ModelClass setterParam = getSetterCall().getParameterTypes()[0];
        ModelClass resolvedType = getExpr().getResolvedType();
        if (setterParam == null || resolvedType == null) {
            return;
        }
        if (!setterParam.isNullable() && resolvedType.isNullable()
                && mExpr.getResolvedType().unbox() != mExpr.getResolvedType()) {
            L.w(ErrorMessages.BOXED_VALUE_CASTING, mExpr, mName, mExpr);
            mExpr = exprModel.safeUnbox(mExpr);
            mExpr.markAsBindingExpression();
        }
    }

    public void unwrapObservableFieldExpression() {
        mExpr = mExpr.unwrapObservableField();
    }

    private static class ViewStubSetterCall extends SetterStore.SetterCall {
        private final String mName;

        public ViewStubSetterCall(String name) {
            mName = name.substring(name.lastIndexOf(':') + 1);
        }

        @Override
        protected String toJavaInternal(String componentExpression, String viewExpression,
                String converted) {
            return "if (" + viewExpression + ".isInflated()) " + viewExpression +
                    ".getBinding().setVariable(BR." + mName + ", " + converted + ")";
        }

        @Override
        protected String toJavaInternal(String componentExpression, String viewExpression,
                String oldValue, String converted) {
            return null;
        }

        @Override
        public int getMinApi() {
            return 0;
        }

        @Override
        public boolean requiresOldValue() {
            return false;
        }

        @Override
        public ModelClass[] getParameterTypes() {
            return new ModelClass[] {
                    ModelAnalyzer.getInstance().findClass(Object.class)
            };
        }

        @Override
        public String getBindingAdapterInstanceClass() {
            return null;
        }

        @Override
        public String getDescription() {
            return "ViewDataBinding.setVariable(BR." + mName + ", value)";
        }
    }

    private static class ViewStubDirectCall extends SetterStore.SetterCall {
        private final SetterStore.SetterCall mWrappedCall;

        public ViewStubDirectCall(String name, ModelClass viewType, ModelClass resolvedType,
                Map<String, String> imports) {
            mWrappedCall = SetterStore.get().getSetterCall(name,
                    viewType, resolvedType, imports);
            if (mWrappedCall == null) {
                L.e("Cannot find the setter for attribute '%s' on %s with parameter type %s.",
                        name, viewType, resolvedType);
            }
        }

        @Override
        protected String toJavaInternal(String componentExpression, String viewExpression,
                String converted) {
            return "if (!" + viewExpression + ".isInflated()) " +
                    mWrappedCall.toJava(componentExpression, viewExpression + ".getViewStub()",
                            null, converted);
        }

        @Override
        protected String toJavaInternal(String componentExpression, String viewExpression,
                String oldValue, String converted) {
            return null;
        }

        @Override
        public int getMinApi() {
            return 0;
        }

        @Override
        public boolean requiresOldValue() {
            return false;
        }

        @Override
        public ModelClass[] getParameterTypes() {
            return new ModelClass[] {
                    ModelAnalyzer.getInstance().findClass(Object.class)
            };
        }

        @Override
        public String getBindingAdapterInstanceClass() {
            return mWrappedCall.getBindingAdapterInstanceClass();
        }

        @Override
        public String getDescription() {
            return mWrappedCall.getDescription();
        }
    }

    private static class IncludeVisibilityCall extends SetterStore.SetterCall {

        @Override
        public boolean requiresOldValue() {
            return false;
        }

        @Override
        public ModelClass[] getParameterTypes() {
            return new ModelClass[] {
                ModelAnalyzer.getInstance().loadPrimitive("int")
            };
        }

        @Override
        public String getBindingAdapterInstanceClass() {
            return null;
        }

        @Override
        public String getDescription() {
            return "setVisibility(value)";
        }

        @Override
        protected String toJavaInternal(String componentExpression, String viewExpression,
                String converted) {
            return viewExpression + ".getRoot().setVisibility(" + converted + ")";
        }

        @Override
        protected String toJavaInternal(String componentExpression, String viewExpression,
                String oldValue, String converted) {
            return null;
        }

        @Override
        public int getMinApi() {
            return 0;
        }
    }
}
