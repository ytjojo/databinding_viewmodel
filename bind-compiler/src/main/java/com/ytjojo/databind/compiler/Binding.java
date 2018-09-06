package com.ytjojo.databind.compiler;

import com.ytjojo.databind.compiler.annotationprocessor.FieldHolder;
import com.ytjojo.databind.compiler.annotationprocessor.MethodHolder;

import java.util.ArrayList;

/**
 * Created by jiulongteng on 2018/8/21.
 */

public class Binding {
    public String bindingKey;
    public ArrayList<String> viewIds = new ArrayList<>();
    public FieldHolder fieldHolder;
    public MethodHolder methodHolder;

}
