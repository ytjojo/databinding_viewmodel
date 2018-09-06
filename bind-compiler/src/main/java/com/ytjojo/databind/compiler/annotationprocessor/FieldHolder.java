package com.ytjojo.databind.compiler.annotationprocessor;

import java.util.HashSet;

import javax.lang.model.element.VariableElement;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class FieldHolder {
    public String fieldName;
    public String getterMethod;
    public String setterMethod;
    public String tripPrefixPropertyName;
    public String typeName;
    public boolean needTransforLiveData;

    VariableElement variableElement;


}
