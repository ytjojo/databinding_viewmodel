package com.ytjojo.databind.compiler.annotationprocessor;

import com.databinding.annotationprocessor.ProcessExpressions;
import com.databinding.annotationprocessor.Utils;
import com.databinding.tool.util.GenerationalClassUtil;
import com.google.auto.service.AutoService;
import com.ytjojo.databind.compiler.tool.store.ResourceBundle;
import com.ytjojo.databind.compiler.tool.util.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.xml.bind.JAXBException;

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

    public static String layoutInfoDir;
    public static String modulePackage;
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        if (roundEnvironment.processingOver()) {
            return false;
        }
        return new FieldProcessor().process(roundEnvironment,layoutInfoDir);

    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        sFiler = processingEnv.getFiler();                  // Generate class.
        sTypes = processingEnv.getTypeUtils();            // Get type utils.
        sElements = processingEnv.getElementUtils();      // Get class meta.
        Logger.init(processingEnvironment.getMessager());

        layoutInfoDir =processingEnv.getOptions().get("GradleVariantConfiguration_DirName");
        modulePackage = processingEnvironment.getOptions().get("binding_modulePackage");
        Logger.get().warning("dirName    "+layoutInfoDir);

        bindingViewModleElement = processingEnvironment.getElementUtils().getTypeElement("com.ytjojo.databind.annotation.BindingViewModel");

    }


    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
