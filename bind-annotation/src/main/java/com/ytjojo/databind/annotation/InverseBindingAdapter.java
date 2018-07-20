package com.ytjojo.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface InverseBindingAdapter {
    String attribute();

    String event() default "";
}