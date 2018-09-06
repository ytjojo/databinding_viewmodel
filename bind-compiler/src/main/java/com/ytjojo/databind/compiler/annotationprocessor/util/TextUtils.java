package com.ytjojo.databind.compiler.annotationprocessor.util;

/**
 * Created by jiulongteng on 2018/8/30.
 */

public class TextUtils {

    public static String toLowerCaseFirstChar(String s){
        if(Character.isLowerCase(s.charAt(0)))
            return s;
        else
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
    }
}
