package com.ytjojo.databind.compiler;

import com.databinding.tool.processing.Scope;
import com.databinding.tool.processing.ScopedException;
import com.databinding.tool.util.L;
import com.ytjojo.databind.compiler.annotationprocessor.BindingHolder;
import com.ytjojo.databind.compiler.tool.store.ResourceBundle;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jiulongteng on 2018/8/21.
 */

public class DataBinder {


    ArrayList<BindingHolder> mAllBindingHolders = new ArrayList<>();


    public void addBindHolder(BindingHolder bindingHolder){
        mAllBindingHolders.add(bindingHolder);
    }
    public boolean resolve(ResourceBundle resourceBundle){
        if(resourceBundle == null){
            return true;
        }
        HashMap<String,BindingHolder> bindingHolderHashMap = new HashMap<>();
        for(BindingHolder holder: mAllBindingHolders){

            String layoutId= holder.layoutId;
            if(bindingHolderHashMap.containsKey(layoutId)){
                L.e("dumplicate model define @BindingViewModel in ",holder.modelTypeElement.getQualifiedName());
            }
            bindingHolderHashMap.put(layoutId,holder);
        }
        for(ResourceBundle.LayoutFileBundle bundle :
                resourceBundle.getLayoutFileBundlesInSource()) {
            try {
                BindingHolder bindingHolder =  bindingHolderHashMap.get(bundle.getFileName());
                if(bindingHolder == null){
                    continue;
                }
                bindingHolder.setLayoutFileBundle(bundle);
                bindingHolder.init();

            } catch (ScopedException ex) {
                Scope.defer(ex);
            }
        }
        return true;

    }
}
