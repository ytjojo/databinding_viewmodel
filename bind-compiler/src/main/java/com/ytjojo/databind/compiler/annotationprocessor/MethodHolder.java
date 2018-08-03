package com.ytjojo.databind.compiler.annotationprocessor;

import java.util.HashSet;

import javax.lang.model.element.ExecutableElement;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class MethodHolder {

    public String methodName;
    public String methodReturnTypeName;
    public HashSet<String> viewIds;
    public String bindingKey;
    private HashSet<Integer> mIds;
    ExecutableElement executableElement;
    public void addId(Integer id){
        if(mIds == null){
            mIds= new HashSet<>();
        }
        mIds.add( id);
    }
    public void addIds(int[] idArray){
        if(mIds == null){
            mIds= new HashSet<>();
        }
        if(idArray != null){
            for( int i: idArray){
                mIds.add(i);
            }
        }
    }
}
