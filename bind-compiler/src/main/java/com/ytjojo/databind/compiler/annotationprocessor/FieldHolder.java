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
    public HashSet<String> viewIds;
    public String bindingKey;
    public boolean needTransforLiveData;
    VariableElement variableElement;
    private HashSet<Integer> mIds;
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