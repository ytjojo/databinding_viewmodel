package com.ytjojo.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
public @interface InverseBindingMethods {
    InverseBindingMethod[] value();
}