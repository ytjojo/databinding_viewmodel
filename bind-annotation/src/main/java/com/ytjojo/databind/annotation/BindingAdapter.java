package com.ytjojo.databind.annotation;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(value={ElementType.METHOD})
public @interface BindingAdapter {
    public boolean requireAll() default true;

    public String[] value();
}
