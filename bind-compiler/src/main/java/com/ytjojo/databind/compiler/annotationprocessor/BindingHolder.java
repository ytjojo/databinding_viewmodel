package com.ytjojo.databind.compiler.annotationprocessor;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jiulongteng on 2018/7/25.
 */

public class BindingHolder {

    public String packageName;
    public String className;
    public String layoutId;
    public HashMap<String,ViewHolder> mViewsField;
    public ArrayList<FieldHolder> mFieldsHolders;
    public ArrayList<MethodHolder> mMethodHolders;
    public String modelClassName;

    public void addField(FieldHolder fieldHolder){
        if(mFieldsHolders ==null){
            mFieldsHolders = new ArrayList<>();
        }
        mFieldsHolders.add(fieldHolder);
    }

    public void addMethod(MethodHolder methodHolder){
        if(mMethodHolders ==null){
            mMethodHolders = new ArrayList<>();
        }
        mMethodHolders.add(methodHolder);
    }

}
