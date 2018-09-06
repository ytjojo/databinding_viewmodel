package com.ytjojo.databind.compiler.annotationprocessor;

import java.util.HashSet;

import javax.lang.model.element.ExecutableElement;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class MethodHolder {

    public String methodName;
    public String methodReturnTypeName;
    ExecutableElement executableElement;
}
