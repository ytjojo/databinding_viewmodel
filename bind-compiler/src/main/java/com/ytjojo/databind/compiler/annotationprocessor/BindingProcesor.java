package com.ytjojo.databind.compiler.annotationprocessor;

import com.databinding.annotationprocessor.ProcessExpressions;
import com.databinding.annotationprocessor.Utils;
import com.databinding.tool.util.GenerationalClassUtil;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.auto.service.AutoService;
import com.sun.tools.javac.code.Symbol;
import com.ytjojo.databind.annotation.BindingViewModel;
import com.ytjojo.databind.compiler.tool.util.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Created by jiulongteng on 2018/7/17.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "com.ytjojo.databind.annotation.Producer",
        "com.ytjojo.databind.annotation.BindingAdapter",
        "com.ytjojo.databind.annotation.Consumer",
        "com.ytjojo.databind.annotation.BindingMethods",
        "com.ytjojo.databind.annotation.BindingViewModel",
        "com.ytjojo.databind.annotation.InverseBindingAdapter"}
)
public class BindingProcesor extends AbstractProcessor {
    public static Filer sFiler;
    // File util, write class file into disk.
    public static  Types sTypes;
    public static  Elements sElements;

    Element bindingViewModleElement;
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

//        BindingBuildInfo buildInfo =  BuildInfoUtil.load(roundEnvironment);
        Logger.get().warning("+++++++++++++++++++++++++++");
        if (roundEnvironment.processingOver()) {
            return false;
        }
        new FieldProcessor().process(roundEnvironment);



        return false;
    }
    private List<ProcessExpressions.IntermediateV2> loadDependencyIntermediates() {
        final List<ProcessExpressions.Intermediate> original = GenerationalClassUtil.get().loadObjects(
                GenerationalClassUtil.ExtensionFilter.LAYOUT);
        final List<ProcessExpressions.IntermediateV2> upgraded = new ArrayList<ProcessExpressions.IntermediateV2>(original.size());
        for (ProcessExpressions.Intermediate intermediate : original) {
            final ProcessExpressions.Intermediate updatedIntermediate = intermediate.upgrade();
            upgraded.add((ProcessExpressions.IntermediateV2) updatedIntermediate);
        }
        return upgraded;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        sFiler = processingEnv.getFiler();                  // Generate class.
        sTypes = processingEnv.getTypeUtils();            // Get type utils.
        sElements = processingEnv.getElementUtils();      // Get class meta.
        Logger.init(processingEnvironment.getMessager());


        bindingViewModleElement = processingEnvironment.getElementUtils().getTypeElement("com.ytjojo.databind.annotation.BindingViewModel");

    }

    private ProcessExpressions.IntermediateV2 createIntermediateFromLayouts(String layoutInfoFolderPath,
                                                                            List<ProcessExpressions.IntermediateV2> intermediateList) {
        final Set<String> excludeList = new HashSet<String>();
        for (ProcessExpressions.IntermediateV2 lib : intermediateList) {
            excludeList.addAll(Utils.get(lib));
        }
        final File layoutInfoFolder = new File(layoutInfoFolderPath);
        if (!layoutInfoFolder.isDirectory()) {
            return null;
        }
        ProcessExpressions.IntermediateV2 result = new ProcessExpressions.IntermediateV2();
        for (File layoutFile : layoutInfoFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml") && !excludeList.contains(name);
            }
        })) {
            try {
                result.addEntry(layoutFile.getName(), FileUtils.readFileToString(layoutFile));
            } catch (IOException e) {
            }
        }
        return result;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }
}
