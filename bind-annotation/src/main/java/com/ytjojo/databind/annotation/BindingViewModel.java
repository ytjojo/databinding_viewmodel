package com.ytjojo.databind.annotation;

/**
 * Created by jiulongteng on 2018/7/13.
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;


@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BindingViewModel {
    int layoutId();
    String bindModelClassName() default "";
    String generateClassName() default "";

    public String[] livedataProperties() default { };
}
