package com.ytjojo.databind.compiler.annotationprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.auto.common.MoreElements;
import com.sun.tools.javac.code.Symbol;
import com.ytjojo.databind.annotation.BindingViewModel;
import com.ytjojo.databind.annotation.Consumer;
import com.ytjojo.databind.compiler.Binding;
import com.ytjojo.databind.compiler.DataBinder;
import com.ytjojo.databind.compiler.InverseBinding;
import com.ytjojo.databind.compiler.annotationprocessor.util.ElementUtils;
import com.ytjojo.databind.compiler.annotationprocessor.util.TypeUtil;
import com.ytjojo.databind.compiler.tool.store.ResourceBundle;
import com.ytjojo.databind.compiler.tool.util.Logger;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.xml.bind.JAXBException;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class FieldProcessor {
    DataBinder dataBinder;
    ResourceBundle resourceBindle;


    public List<TypeElement> getClassTypeInAnnotation(Element element, Class<? extends Annotation> clazz, String method) {
        AnnotationMirror svcAnnoMirror =
                MoreElements.getAnnotationMirror(element, clazz).get();

        Optional<? extends Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> optional = svcAnnoMirror.getElementValues().entrySet().stream().filter(new Predicate<Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>>() {
            @Override
            public boolean test(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry) {
                return entry.getKey().getSimpleName().toString().equals(method);
            }
        }).findFirst();
        if(!optional.isPresent()){
            return new ArrayList<>();
        }
        AnnotationValue annotationValue = optional.get().getValue();
        ArrayList<TypeElement> types = new ArrayList<>();
        Object value = annotationValue.getValue();
        if ((value instanceof List)) {
            List<AnnotationValue> values = (List<AnnotationValue>) value;

            for (AnnotationValue item : values) {
                annotationValue = (AnnotationValue) item;
                DeclaredType declaredType = (DeclaredType) annotationValue.getValue();
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                types.add(typeElement);
            }
        } else {
            annotationValue = (AnnotationValue) value;
            DeclaredType declaredType = (DeclaredType) annotationValue.getValue();
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            types.add(typeElement);
        }
        return types;


    }

    public void file(TypeElement element, BindingHolder bindingHolder) {

        List<? extends Element> allMembers = BindingProcesor.sElements.getAllMembers(element);
        List<? extends Element> allFields = ElementFilter.fieldsIn(allMembers);
        List<? extends Element> allMethods = ElementFilter.methodsIn(allMembers);
        ArrayList<VariableElement> privateField = new ArrayList<>();
        ArrayList<VariableElement> publicField = new ArrayList<>();
        HashMap<String, VariableElement> fieldMap = new HashMap<>();
        HashMap<String, ExecutableElement> methodMap = new HashMap<>();

        for (Element e : allMethods) {
            ExecutableElement executable = (ExecutableElement) e;
            Consumer consumer = executable.getAnnotation(Consumer.class);
            MethodHolder methodHolder = new MethodHolder();
            methodHolder.methodReturnTypeName = executable.getReturnType().toString();
            methodHolder.methodName = executable.getSimpleName().toString();
            methodHolder.executableElement = executable;
            bindingHolder.addMethod(methodHolder);
            methodMap.put(executable.getSimpleName().toString(), executable);

            if (consumer != null) {
                if (executable.getReturnType().getKind() == TypeKind.VOID) {
                    Logger.get().error("return type  can't be void");
                    continue;
                }
                List<? extends VariableElement> parameters = executable.getParameters();
                if (parameters != null && parameters.size() > 0) {
                    Logger.get().error(" method must has no parameters ");
                    continue;
                }
                if (consumer.value().length > 0) {
                    Binding binding = new Binding();
                    binding.methodHolder = methodHolder;
                    binding.bindingKey = consumer.attribute();
                    bindingHolder.addBinding(binding);
                }

            }

        }

        for (Element e : allFields) {
            VariableElement var = (VariableElement) e;
            fieldMap.put(var.getSimpleName().toString(), var);
            FieldHolder fieldHolder = new FieldHolder();
            Consumer consumer = var.getAnnotation(Consumer.class);
            fieldHolder.fieldName = var.getSimpleName().toString();
            fieldHolder.variableElement = var;
            fieldHolder.typeName = var.asType().toString();
            if (!var.getModifiers().contains(Modifier.PUBLIC)) {

                HashSet<String> possibleMethodNames = new HashSet<>();
                String elementNameLowerCase = fieldHolder.fieldName.toLowerCase();
                possibleMethodNames.add("get" + elementNameLowerCase);
                String stripPrefixName = ElementUtils.stripPrefixFromField(var).toLowerCase();
                possibleMethodNames.add("get" + stripPrefixName);
                if (var.asType().getKind() == TypeKind.BOOLEAN) {
                    possibleMethodNames.add("is" + elementNameLowerCase);
                    possibleMethodNames.add("has" + elementNameLowerCase);
                    possibleMethodNames.add(elementNameLowerCase);
                    possibleMethodNames.add("is" + stripPrefixName);
                    possibleMethodNames.add("has" + stripPrefixName);
                    possibleMethodNames.add(stripPrefixName);

                }
                for (String method : possibleMethodNames) {
                    ExecutableElement getMethod = methodMap.get(method);
                    if (getMethod != null && getMethod.getReturnType().toString().equals(var.asType().toString()) && getMethod.getParameters().isEmpty()) {
                        fieldHolder.getterMethod = method;
                        break;
                    }
                }
                if (StringUtils.isEmpty(fieldHolder.getterMethod)) {
                    Logger.get().warning("private field can not found getter method  field name is" + fieldHolder.fieldName);
                }
                HashSet<String> settterMethodNames = new HashSet<>();
                settterMethodNames.add("set" + elementNameLowerCase);
                settterMethodNames.add("set" + stripPrefixName);
                for (String method : settterMethodNames) {
                    ExecutableElement setttMethod = methodMap.get(method);
                    if (setttMethod != null && setttMethod.getReturnType().getKind() == TypeKind.VOID && setttMethod.getParameters().get(0).asType().toString().equals(var.asType().toString())) {
                        fieldHolder.setterMethod = method;
                        break;
                    }
                }
                if (StringUtils.isEmpty(fieldHolder.getterMethod)) {
                    Logger.get().warning("private field can not found setter method  field name is" + fieldHolder.fieldName);
                }
            }

            bindingHolder.addField(fieldHolder);
            if (consumer != null) {
                if (consumer.value().length > 0) {
                    Binding binding = new Binding();
                    binding.fieldHolder = fieldHolder;
                    binding.bindingKey = consumer.attribute();
                    bindingHolder.addBinding(binding);
                }
                if (consumer.twoWayId() > 0) {
                    InverseBinding inverseBinding = new InverseBinding();
                    inverseBinding.fieldHolder = fieldHolder;
                    bindingHolder.addInverseBinding(inverseBinding);
                    inverseBinding.bindingKey = consumer.attribute();
                }
            }

        }

    }

    public void findId(TypeElement element,BindingHolder bindingHolder) {
        ElementKind kind = element.getKind();
        if (kind.isClass()) {

            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) element;

            try {
                InputStream input = classSymbol.sourcefile.openInputStream();
                CompilationUnit cu = JavaParser.parse(input);
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                        super.visit(n, arg);
                    }

                    @Override
                    public void visit(SingleMemberAnnotationExpr n, Void arg) {
                        n.getName();
                        super.visit(n, arg);
                    }

                    @Override
                    public void visit(NormalAnnotationExpr n, Void arg) {
                        super.visit(n, arg);
                    }
                }, null);
                Iterator<TypeDeclaration<?>> it = cu.getTypes().iterator();
                while (it.hasNext()) {
                    TypeDeclaration<?> typeDeclaration = it.next();
                    Optional<AnnotationExpr> annoOpt = typeDeclaration.getAnnotationByClass(BindingViewModel.class);
                    NormalAnnotationExpr annoExpr = (NormalAnnotationExpr) annoOpt.get();
                    NodeList<MemberValuePair> memberNodeList = annoExpr.getPairs();
                    Iterator<MemberValuePair> memberIt = memberNodeList.iterator();
                    while (memberIt.hasNext()) {
                        MemberValuePair pair = memberIt.next();

                        if (pair.getName().getIdentifier().equals("layoutId")) {
                            bindingHolder.layoutId = pair.getValue().toString().split("\\.")[2];
                        }
                    }
                    List<MethodDeclaration> methodNodeList = typeDeclaration.getMethods();
                    processMethod(bindingHolder, methodNodeList);
                    List<FieldDeclaration> fieldNodeList = typeDeclaration.getFields();
                    processField(bindingHolder, fieldNodeList);
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.get().warning(element.getSimpleName().toString() + ((TypeElement) element).getQualifiedName().toString());
        }

    }

    public boolean process(RoundEnvironment roundEnvironment,String layoutDir) {
        if(StringUtils.isEmpty(layoutDir)){
            return true;
        }
        if(StringUtils.isEmpty(BindingProcesor.modulePackage)){
            return true;
        }
        dataBinder = new DataBinder();
        Intermediate intermediate = Intermediate.createIntermediateFromLayouts(layoutDir);
        resourceBindle = Intermediate.getResourceBundle(BindingProcesor.modulePackage);
        if(intermediate == null|| intermediate.isLayoutInfoEmpty()){
            return true;
        }
        try {
            intermediate.appendTo(resourceBindle);
        } catch (JAXBException e) {
            e.printStackTrace();
            Logger.get().error(e.toString());
        }
        Set<? extends Element> bindingViewModelElements = roundEnvironment.getElementsAnnotatedWith(BindingViewModel.class);
        for (Element element : bindingViewModelElements) {
            ElementKind kind = element.getKind();
            if (kind == ElementKind.CLASS ) {
                TypeElement typeElement = (TypeElement) element;
                BindingViewModel bindingViewModel = element.getAnnotation(BindingViewModel.class);
                String generateClassName = bindingViewModel.generateClassName();
                List<TypeElement> classes = getClassTypeInAnnotation(element, BindingViewModel.class, "bindModelClass");
                TypeElement modelTypeElement = typeElement;
                if (classes != null && !classes.isEmpty()) {
                    if (!classes.get(0).getQualifiedName().toString().equals(Object.class.getCanonicalName())) {
                        modelTypeElement = classes.get(0);
                    }

                }

                BindingHolder bindingHolder = new BindingHolder();
                bindingHolder.modelTypeElement = typeElement;
                bindingHolder.setModelFullClassName( modelTypeElement.getQualifiedName().toString());
                bindingHolder.mBindingClassName = StringUtils.isNotEmpty(generateClassName) ? generateClassName : element.getSimpleName() + "Binding";
                dataBinder.addBindHolder(bindingHolder);
                file(typeElement,bindingHolder);
                findId(typeElement,bindingHolder);

            }
        }
        return dataBinder.resolve(resourceBindle);
    }

//    public void process(RoundEnvironment roundEnvironment) {
//        Set<? extends Element> bindingViewModelElements = roundEnvironment.getElementsAnnotatedWith(BindingViewModel.class);
//        for (Element element : bindingViewModelElements) {
//            ElementKind kind = element.getKind();
//            if (kind == ElementKind.CLASS || kind == ElementKind.INTERFACE) {
//                BindingViewModel bindingViewModel = element.getAnnotation(BindingViewModel.class);
//                String generateClassName = bindingViewModel.generateClassName();
//
//                Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) element;
//                String className = classSymbol.getQualifiedName().toString();
//
//                BindingHolder bindingHolder = new BindingHolder();
//
//                bindingHolder.className = StringUtils.isNotEmpty(generateClassName) ? generateClassName : element.getSimpleName() + "Binding";
//                try {
//                    InputStream input = classSymbol.sourcefile.openInputStream();
//                    CompilationUnit cu = JavaParser.parse(input);
//                    cu.accept(new VoidVisitorAdapter<Void>() {
//                        @Override
//                        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
//                            super.visit(n, arg);
//                        }
//
//                        @Override
//                        public void visit(SingleMemberAnnotationExpr n, Void arg) {
//                            n.getName();
//                            super.visit(n, arg);
//                        }
//
//                        @Override
//                        public void visit(NormalAnnotationExpr n, Void arg) {
//                            super.visit(n, arg);
//                        }
//                    }, null);
//                    Iterator<TypeDeclaration<?>> it = cu.getTypes().iterator();
//                    while (it.hasNext()) {
//                        TypeDeclaration<?> typeDeclaration = it.next();
//                        Optional<AnnotationExpr> annoOpt = typeDeclaration.getAnnotationByClass(BindingViewModel.class);
//                        NormalAnnotationExpr annoExpr = (NormalAnnotationExpr) annoOpt.get();
//                        NodeList<MemberValuePair> memberNodeList = annoExpr.getPairs();
//                        Iterator<MemberValuePair> memberIt = memberNodeList.iterator();
//                        while (memberIt.hasNext()) {
//                            MemberValuePair pair = memberIt.next();
//
//                            if (pair.getName().getIdentifier().equals("layoutId")) {
//                                bindingHolder.layoutId = pair.getValue().toString();
//                            } else if (pair.getName().getIdentifier().equals("bindModelClassName")) {
//                                String modelClassName = pair.getValue().toString();
//                                modelClassName = modelClassName.equals("java.lang.Object") ? className : modelClassName;
//                                if (!className.equals(modelClassName)) {
//                                    Logger.get().error("BindingViewModel generateClassName should be " + className);
//                                }
//                                bindingHolder.modelClassName = modelClassName;
//                            }
//                        }
//                        List<MethodDeclaration> methodNodeList = typeDeclaration.getMethods();
//                        processMethod(bindingHolder, methodNodeList);
//                        List<FieldDeclaration> fieldNodeList = typeDeclaration.getFields();
//                        processField(bindingHolder, fieldNodeList);
//                    }
//
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                Logger.get().warning(element.getSimpleName().toString() + ((TypeElement) element).getQualifiedName().toString());
//
//
//            } else if (kind == ElementKind.METHOD) {
//                Element enclosingElement = element.getEnclosingElement();
//                kind = enclosingElement.getKind();
//                if (kind != ElementKind.CLASS && kind != ElementKind.INTERFACE) {
//                    continue;
//                }
//                Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) enclosingElement;
//                BindingViewModel bindingViewModel = element.getAnnotation(BindingViewModel.class);
//                try {
//                    InputStream input = classSymbol.sourcefile.openInputStream();
//                    CompilationUnit cu = JavaParser.parse(input);
//                    Iterator<TypeDeclaration<?>> it = cu.getTypes().iterator();
//                    String layoutId = "";
//                    while (it.hasNext()) {
//                        TypeDeclaration<?> typeDeclaration = it.next();
//                        List<MethodDeclaration> methodNodeList = typeDeclaration.getMethods();
//                        for (MethodDeclaration md : methodNodeList) {
//                            Optional<AnnotationExpr> annoOpt = md.getAnnotationByClass(BindingViewModel.class);
//                            if (annoOpt != null) {
//                                BindingHolder holder = new BindingHolder();
//
//                                NormalAnnotationExpr annoExpr = (NormalAnnotationExpr) annoOpt.get();
//                                NodeList<MemberValuePair> memberNodeList = annoExpr.getPairs();
//                                Iterator<MemberValuePair> memberIt = memberNodeList.iterator();
//                                while (memberIt.hasNext()) {
//                                    MemberValuePair pair = memberIt.next();
//
//                                    if (pair.getName().getIdentifier().equals("layoutId")) {
//                                        holder.layoutId = pair.getValue().toString();
//                                    }
//                                }
//
//
//                            }
//                        }
//
//                    }
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//
//            } else {
//                Logger.get().warning("BindingViewModel must be on a member  method or type class or interface. The  type is %s",
//                        element.getKind());
//            }
//        }
//    }

    private void getAnnotationValue(Element element) {

        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();

        for (AnnotationMirror mirror : annotationMirrors) {
            if ((mirror.getAnnotationType().asElement().toString()).equals(TypeUtil.bindingViewModleElement.toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {


                    if ("layoutId".equals(entry.getKey().getSimpleName().toString())) {
                        AnnotationValue value = entry.getValue();
                        String layoutId = value.toString();
                        Logger.get().warning(mirror.getAnnotationType().asElement().getSimpleName() + layoutId);
                    }
                }
            }
        }
    }

    private void processMethod(BindingHolder bindingHolder, List<MethodDeclaration> methodNodeList) {
        for (MethodDeclaration md : methodNodeList) {
            Optional<AnnotationExpr> annoOpt = md.getAnnotationByClass(Consumer.class);
            if (annoOpt != null) {

                if (!annoOpt.isPresent()) {
                    continue;
                }
                String methodName = md.getNameAsString();
                Binding binding = null;
                for (Binding item : bindingHolder.mAllBinding) {
                    if (item.methodHolder.methodName.equals(methodName)) {
                        binding = item;
                        break;
                    }
                }
                AnnotationExpr annotationExpr = annoOpt.get();
                if (annotationExpr instanceof SingleMemberAnnotationExpr) {
                    SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotationExpr;
                    String id = singleMemberAnnotationExpr.getMemberValue().toString();

                    binding.viewIds.add(tripToId(id));

                } else {
                    NormalAnnotationExpr annoExpr = (NormalAnnotationExpr) annoOpt.get();

                    NodeList<MemberValuePair> memberNodeList = annoExpr.getPairs();

                    Iterator<MemberValuePair> memberIt = memberNodeList.iterator();

                    while (memberIt.hasNext()) {
                        MemberValuePair pair = memberIt.next();

                        if (pair.getName().getIdentifier().equals("value")) {
                            String value = pair.getValue().toString().trim();
                            value = value.replaceAll("\\{", "").replaceAll("}", "");
                            binding.viewIds.addAll(tripToIdList(value));

                        }
                    }
                }

            }
        }
    }

    private void processField(BindingHolder bindingHolder, List<FieldDeclaration> fieldNodeList) {
        for (FieldDeclaration fd : fieldNodeList) {
            Optional<AnnotationExpr> annoOpt = fd.getAnnotationByClass(Consumer.class);
            if (annoOpt != null) {
                if (!annoOpt.isPresent()) {
                    continue;
                }

                NodeList<VariableDeclarator> variableDeclaratorsNodeList = fd.getVariables();
                Binding binding = null;
                InverseBinding inverseBinding = null;
                for (VariableDeclarator variableDeclarator : variableDeclaratorsNodeList) {
                    String fieldName = variableDeclarator.getName().toString();
                    for (Binding item : bindingHolder.mAllBinding) {
                        if (item.fieldHolder != null && item.fieldHolder.fieldName.equals(fieldName)) {
                            binding = item;
                        }
                    }
                    for (InverseBinding item : bindingHolder.mAllInversBinding) {
                        if (item.fieldHolder != null && item.fieldHolder.fieldName.equals(fieldName)) {
                            inverseBinding = item;
                        }
                    }

                }

                AnnotationExpr annotationExpr = annoOpt.get();
                if (annotationExpr instanceof SingleMemberAnnotationExpr) {
                    SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotationExpr;
                    String id = singleMemberAnnotationExpr.getMemberValue().toString();
                    if (binding != null) {
                        binding.viewIds.add(tripToId(id));

                    }
//                    fieldHolder.viewIds = new HashSet<>();
//                    fieldHolder.viewIds.addAll(Arrays.asList(id));

                } else {
                    NormalAnnotationExpr annoExpr = (NormalAnnotationExpr) annoOpt.get();
                    NodeList<MemberValuePair> memberNodeList = annoExpr.getPairs();
                    Iterator<MemberValuePair> memberIt = memberNodeList.iterator();

                    while (memberIt.hasNext()) {
                        MemberValuePair pair = memberIt.next();

                        if (pair.getName().getIdentifier().equals("value")) {
                            String value = pair.getValue().toString().trim();
                            binding.viewIds.addAll(tripToIdList(value));

                        } else if (pair.getName().getIdentifier().equals("twoWayId")) {

                            inverseBinding.viewId = tripToId(pair.getValue().toString());
                        }
                    }
                }

            }
        }
    }

    private String tripToId(String expr){
        String[] array = expr.split("\\.");
        return array[array.length - 1].trim();
    }

    private List<String> tripToIdList(String expr){
        String value = expr.replaceAll("\\{", "").replaceAll("}", "");
        ArrayList<String> list = new ArrayList<>();
        for (String idExp : value.split(",")) {

            list.add(tripToId(idExp));
        }
        return list;
    }


}
