package com.ytjojo.databind.compiler.annotationprocessor.util;

import com.ytjojo.databind.compiler.annotationprocessor.BindingProcesor;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class TypeUtil {

    public static Element collectionElement= BindingProcesor.sElements.getTypeElement("java.util.Map");
    public static Element mapElement= BindingProcesor.sElements.getTypeElement("java.util.Collection");
    public static Element bindingViewModleElement = BindingProcesor.sElements.getTypeElement("com.ytjojo.databind.annotation.BindingViewModel");


    public static TypeMirror  getTypeMirror(String fullClassName){

       return BindingProcesor.sElements.getTypeElement(fullClassName).asType();

    }

}
