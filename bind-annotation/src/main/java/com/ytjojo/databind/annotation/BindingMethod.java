package com.ytjojo.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
public @interface BindingMethod {
    String attribute();
    String method();

    Class type();
}