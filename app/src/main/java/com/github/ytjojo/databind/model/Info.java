package com.github.ytjojo.databind.model;

import com.github.ytjojo.databind.R;
import com.ytjojo.databind.annotation.BindingViewModel;
import com.ytjojo.databind.annotation.Consumer;

/**
 * Created by jiulongteng on 2018/7/23.
 */
@BindingViewModel(layoutId = R.layout.layout_bind_info, generateClassName = "InfoBinding")
public class Info extends Itemparent{

    @Consumer(value = {R.id.tv_name,R.id.tv_name1})
    public String name;
    @Consumer(value = {R.id.tv_type},twoWayId = R.id.tv_type)
    private  String type;

    public InfoItem item;


    @Consumer(R.id.tv_name)
    public String getTitle(){
        return "ssssss";
    }



}
