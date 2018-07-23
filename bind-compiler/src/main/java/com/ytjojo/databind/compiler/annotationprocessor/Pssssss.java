package com.ytjojo.databind.compiler.annotationprocessor;

import android.databinding.BindingBuildInfo;
import android.databinding.annotationprocessor.BuildInfoUtil;
import android.databinding.annotationprocessor.ProcessExpressions;
import android.databinding.annotationprocessor.Utils;
import android.databinding.tool.reflection.ModelAnalyzer;
import android.databinding.tool.store.ResourceBundle;
import android.databinding.tool.store.SetterStore;
import android.databinding.tool.util.GenerationalClassUtil;
import android.databinding.tool.util.L;

import com.google.auto.service.AutoService;
import com.ytjojo.databind.annotation.BindingViewModel;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
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
public class Pssssss extends AbstractProcessor {
    private Filer mFiler;       // File util, write class file into disk.
    private Types types;
    private Elements elements;

    Element bindingViewModleElement;
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        BindingBuildInfo buildInfo =  BuildInfoUtil.load(roundEnvironment);
        roundEnvironment.getRootElements();



        Set<? extends Element> bindingViewModelElements =roundEnvironment.getElementsAnnotatedWith(BindingViewModel.class);
        for(Element element: bindingViewModelElements){
            ElementKind kind= element.getKind();
            if(kind == ElementKind.CLASS ){
               BindingViewModel bindingViewModel =element.getAnnotation(BindingViewModel.class);
               String generateClassName =  bindingViewModel.gegerateClassName();
                Class modelClass =bindingViewModel.bindModelClass();
                String layoutId = "";

                List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
                for(AnnotationMirror mirror:annotationMirrors){
                    if((mirror.getAnnotationType().asElement()).equals(bindingViewModleElement)){
                        for(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry:mirror.getElementValues().entrySet()){
                            if("layoutId".equals(entry.getKey().getSimpleName().toString())){
                                AnnotationValue value=entry.getValue();
                                layoutId= value.toString();
                                L.e(mirror.getAnnotationType().asElement().getSimpleName() +layoutId );
                            }
                        }
                    }
                }


            }else if(kind ==ElementKind.METHOD && kind == ElementKind.INTERFACE){



            }else {
                L.e("BindingViewModel must be on a member  method or type class or interface. The  type is %s",
                        element.getKind());
            }
        }

        final ModelAnalyzer modelAnalyzer = ModelAnalyzer.getInstance();
        SetterStore store = SetterStore.get();
        L.e("+++++++++++++++++++++++++++");

        ResourceBundle resourceBundle = new ResourceBundle(buildInfo.modulePackage());
        List intermediateList = this.loadDependencyIntermediates();
        Iterator mine = intermediateList.iterator();
        L.e("+++++++++++++++++++++++++++");

        while(mine.hasNext()) {
            ProcessExpressions.Intermediate t = (ProcessExpressions.Intermediate)mine.next();
            L.e("+++++++++++++++++++++++++++");

            try {
                t.appendTo(resourceBundle,true);
            } catch (Throwable var10) {
                L.e(var10, "unable to prepare resource bundle", new Object[0]);
            }
        }

        ProcessExpressions.IntermediateV2 mine1 = this.createIntermediateFromLayouts(buildInfo.layoutInfoDir(), intermediateList);
        mine1.log();
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
        mFiler = processingEnv.getFiler();                  // Generate class.
        types = processingEnv.getTypeUtils();            // Get type utils.
        elements = processingEnv.getElementUtils();      // Get class meta.



        bindingViewModleElement = processingEnvironment.getElementUtils().getTypeElement("com.ytjojo.databind.annotation,BindingViewModel");

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

}
