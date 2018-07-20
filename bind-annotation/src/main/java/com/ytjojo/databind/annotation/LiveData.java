package com.ytjojo.databind.annotation;

/**
 * Created by jiulongteng on 2018/7/13.
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME) // this is necessary for java analyzer to work
public @interface LiveData {
    boolean ignore() default false;
}
