package com.ytjojo.databind.compiler.annotationprocessor;

import com.databinding.tool.BindingTarget;
import com.databinding.tool.ExpressionParser;
import com.databinding.tool.expr.ExprModel;
import com.databinding.tool.expr.IdentifierExpr;
import com.databinding.tool.ext.ExtKt;
import com.databinding.tool.processing.Scope;
import com.databinding.tool.store.Location;
import com.databinding.tool.util.L;
import com.databinding.tool.util.Preconditions;
import com.databinding.tool.writer.LayoutBinderWriter;
import com.databinding.tool.writer.LayoutBinderWriterKt;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.ytjojo.databind.compiler.Binding;
import com.ytjojo.databind.compiler.BindingUtilsKt;
import com.ytjojo.databind.compiler.InverseBinding;
import com.ytjojo.databind.compiler.annotationprocessor.util.TextUtils;
import com.ytjojo.databind.compiler.annotationprocessor.util.TypeUtil;
import com.ytjojo.databind.compiler.tool.store.ResourceBundle;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class BindingHolder {

    public String mModulePackage;
    public String mBindingClassName;
    public String layoutId;
    public HashMap<String, ViewHolder> mViewsField = new HashMap<>();
    public ArrayList<FieldHolder> mFieldsHolders;
    public ArrayList<MethodHolder> mMethodHolders;

    private String modelFullClassName;
    ResourceBundle.LayoutFileBundle layoutFileBundle;
    public ArrayList<Binding> mAllBinding = new ArrayList<>();
    public ArrayList<InverseBinding> mAllInversBinding = new ArrayList<>();
    public TypeElement modelTypeElement;
    public String modelFieldName;
    public void setModelFullClassName(String modelFullClassName){
        this.modelFullClassName= modelFullClassName;
        this.modelFieldName = TextUtils.toLowerCaseFirstChar(BindingProcesor.sElements.getTypeElement(modelFullClassName)
                .getSimpleName().toString())+"BindingModel";
    }

    public void addField(FieldHolder fieldHolder) {
        if (mFieldsHolders == null) {
            mFieldsHolders = new ArrayList<>();
        }
        mFieldsHolders.add(fieldHolder);
    }

    public void addMethod(MethodHolder methodHolder) {
        if (mMethodHolders == null) {
            mMethodHolders = new ArrayList<>();
        }
        mMethodHolders.add(methodHolder);
    }

    public void setLayoutFileBundle(ResourceBundle.LayoutFileBundle layoutFileBundle) {
        this.layoutFileBundle = layoutFileBundle;
        for (ResourceBundle.BindingTargetBundle bindingTargetBundle : layoutFileBundle.getBindingTargetBundles()) {
            if (StringUtils.isEmpty(bindingTargetBundle.getId())) {
                continue;
            }
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.viewId = bindingTargetBundle.getId();
            viewHolder.viewFieldName = BindingUtilsKt.idToField(bindingTargetBundle.getId());
            viewHolder.viewTypeName = bindingTargetBundle.getFullClassName();
            viewHolder.bindingTargetBundle = bindingTargetBundle;

            mViewsField.put(bindingTargetBundle.getId(), viewHolder);

        }
    }

    private static final ClassName VIEW_DATA_BINDING =
            ClassName.get("com.ytjojo.databind", "BaseViewDataBinding");
    private static final ClassName ANDROID_VIEW = ClassName.get("android.view", "View");
    private static final ClassName ANDROID_LAYOUT_INFLATOR = ClassName.get("android.view", "LayoutInflater");
    private static final ClassName ANDROID_VIEW_GROUP = ClassName.get("android.view", "ViewGroup");
    private static final ClassName NULLABLE = ClassName.get("android.support.annotation", "Nullable");
    private static final ClassName NON_NULL = ClassName.get("android.support.annotation", "NonNull");

    public void generateJava() {
        TypeSpec.classBuilder(mBindingClassName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(VIEW_DATA_BINDING)
                .addFields(createBindingTargetFields())
                .addFields(createVariables());
//                .addTypeVariable();


    }

    private List<FieldSpec> createBindingTargetFields() {
        ArrayList<FieldSpec> list = new ArrayList<>();
        for (Map.Entry<String, ViewHolder> entry : mViewsField.entrySet()) {
            ViewHolder viewHolder = entry.getValue();
            TypeName typeName = TypeName.get(TypeUtil.getTypeMirror(viewHolder.viewTypeName));
            FieldSpec fieldSpec = FieldSpec.builder(typeName, viewHolder.viewFieldName, Modifier.PUBLIC)
                    .build();
            list.add(fieldSpec);
        }
        return list;

    }
    public void addBinding(Binding binding){
        mAllBinding.add(binding);
    }
    public void addInverseBinding(InverseBinding inverseBinding){
        mAllInversBinding.add(inverseBinding);
    }

    private List<FieldSpec> createVariables() {
        ArrayList<FieldSpec> list =new ArrayList<>();
        TypeMirror modelTypeMirror = TypeUtil.getTypeMirror(modelFullClassName);
        TypeName modelTypeName = TypeName.get(modelTypeMirror);
        list.add(FieldSpec.builder(modelTypeName,modelFieldName,Modifier.PRIVATE).build());

        return list;


    }


    private static final Comparator<BindingTarget> COMPARE_FIELD_NAME = new Comparator<BindingTarget>() {
        @Override
        public int compare(BindingTarget first, BindingTarget second) {
            final String fieldName1 = LayoutBinderWriterKt.getFieldName(first);
            final String fieldName2 = LayoutBinderWriterKt.getFieldName(second);
            return fieldName1.compareTo(fieldName2);
        }
    };

    /*
    * val pkg: String, val projectPackage: String, val baseClassName: String,
        val layoutName:String, val lb: LayoutExprBinding*/
    private  ExprModel mExprModel;
    private  ExpressionParser mExpressionParser;
    private  List<BindingTarget> mBindingTargets;
    private  List<BindingTarget> mSortedBindingTargets;
    private  HashMap<String, String> mUserDefinedVariables = new HashMap<String, String>();

    private LayoutBinderWriter mWriter;
    private static final String[] sJavaLangClasses = {
            "Deprecated",
            "Override",
            "SafeVarargs",
            "SuppressWarnings",
            "Appendable",
            "AutoCloseable",
            "CharSequence",
            "Cloneable",
            "Comparable",
            "Iterable",
            "Readable",
            "Runnable",
            "Thread.UncaughtExceptionHandler",
            "Boolean",
            "Byte",
            "Character",
            "Character.Subset",
            "Character.UnicodeBlock",
            "Class",
            "ClassLoader",
            "Compiler",
            "Double",
            "Enum",
            "Float",
            "InheritableThreadLocal",
            "Integer",
            "Long",
            "Math",
            "Number",
            "Object",
            "Package",
            "Process",
            "ProcessBuilder",
            "Runtime",
            "RuntimePermission",
            "SecurityManager",
            "Short",
            "StackTraceElement",
            "StrictMath",
            "String",
            "StringBuffer",
            "StringBuilder",
            "System",
            "Thread",
            "ThreadGroup",
            "ThreadLocal",
            "Throwable",
            "Void",
            "Thread.State",
            "ArithmeticException",
            "ArrayIndexOutOfBoundsException",
            "ArrayStoreException",
            "ClassCastException",
            "ClassNotFoundException",
            "CloneNotSupportedException",
            "EnumConstantNotPresentException",
            "Exception",
            "IllegalAccessException",
            "IllegalArgumentException",
            "IllegalMonitorStateException",
            "IllegalStateException",
            "IllegalThreadStateException",
            "IndexOutOfBoundsException",
            "InstantiationException",
            "InterruptedException",
            "NegativeArraySizeException",
            "NoSuchFieldException",
            "NoSuchMethodException",
            "NullPointerException",
            "NumberFormatException",
            "ReflectiveOperationException",
            "RuntimeException",
            "SecurityException",
            "StringIndexOutOfBoundsException",
            "TypeNotPresentException",
            "UnsupportedOperationException",
            "AbstractMethodError",
            "AssertionError",
            "ClassCircularityError",
            "ClassFormatError",
            "Error",
            "ExceptionInInitializerError",
            "IllegalAccessError",
            "IncompatibleClassChangeError",
            "InstantiationError",
            "InternalError",
            "LinkageError",
            "NoClassDefFoundError",
            "NoSuchFieldError",
            "NoSuchMethodError",
            "OutOfMemoryError",
            "StackOverflowError",
            "ThreadDeath",
            "UnknownError",
            "UnsatisfiedLinkError",
            "UnsupportedClassVersionError",
            "VerifyError",
            "VirtualMachineError",
    };
    public BindingHolder(){

    }

    public void init(){
        mExprModel = new ExprModel(mModulePackage, true);
        mExpressionParser = new ExpressionParser(mExprModel);
        mModulePackage = BindingProcesor.modulePackage;
        mBindingTargets = new ArrayList<BindingTarget>();
        HashSet<String> names = new HashSet<String>();
        for(Binding binding:mAllBinding){
            if(binding.fieldHolder != null){
                IdentifierExpr identifierExpr = addVariable(binding.fieldHolder.fieldName,binding.fieldHolder.typeName,null,true);
                names.add(identifierExpr.getName());
            }
        }
        for (String javaLangClass : sJavaLangClasses) {
            mExprModel.addImport(javaLangClass, "java.lang." + javaLangClass, null);
        }

        for (ResourceBundle.BindingTargetBundle targetBundle : layoutFileBundle.getBindingTargetBundles()) {
            try {
                Scope.enter(targetBundle);
                final BindingTarget bindingTarget = createBindingTarget(targetBundle);
                if (bindingTarget.getId() != null) {
                    final String fieldName = LayoutBinderWriterKt.
                            getReadableName(bindingTarget);
                    if (names.contains(fieldName)) {
                        L.w("View field %s collides with a variable or import", fieldName);
                    } else {
                        names.add(fieldName);
                        mExprModel.viewFieldExpr(bindingTarget);
                    }
                }
            } finally {
                Scope.exit();
            }
        }


    }

    public BindingTarget createBindingTarget(ResourceBundle.BindingTargetBundle targetBundle) {
        com.databinding.tool.store.ResourceBundle.BindingTargetBundle bundle =new com.databinding.tool.store.ResourceBundle.BindingTargetBundle();
        bundle.mId = targetBundle.mId;
        bundle.mViewName = targetBundle.mViewName;
        bundle.mLocation= targetBundle.mLocation;
        bundle.mIncludedLayout= targetBundle.mIncludedLayout;
        for(ResourceBundle.BindingTargetBundle.BindingBundle item:targetBundle.mBindingBundleList){
            bundle.addBinding(item.getName(),item.getExpr(),item.isTwoWay(),item.getLocation(),item.getValueLocation());
        }
        for(Binding binding:mAllBinding){
            if(binding.fieldHolder != null){
                if(binding.viewIds.contains(targetBundle.getId())){
                    bundle.addBinding(binding.bindingKey,binding.fieldHolder.fieldName,false,new Location(),new Location());
                }

            }else if(binding.methodHolder!=null){
                if(binding.viewIds.contains(targetBundle.getId())){
                    bundle.addBinding(binding.bindingKey,binding.methodHolder.methodName,false,new Location(),new Location());
                }
            }
        }

        for(InverseBinding binding:mAllInversBinding){
            if(binding.viewId.equals(targetBundle.getId())){
                bundle.addBinding(binding.bindingKey,binding.fieldHolder.fieldName,true,new Location(),new Location());
            }
        }

        final BindingTarget target = new BindingTarget(bundle);
        mBindingTargets.add(target);
        target.setModel(mExprModel);
        return target;
    }

    public IdentifierExpr addVariable(String name, String type, Location location,
                                      boolean declared) {
        Preconditions.check(!mUserDefinedVariables.containsKey(name),
                "%s has already been defined as %s", name, type);
        final IdentifierExpr id = mExprModel.identifier(name);
        id.setUserDefinedType(type);
        id.enableDirectInvalidation();
        if (location != null) {
            id.addLocation(location);
        }
        mUserDefinedVariables.put(name, type);
        if (declared) {
            id.setDeclared();
        }
        return id;
    }

}
