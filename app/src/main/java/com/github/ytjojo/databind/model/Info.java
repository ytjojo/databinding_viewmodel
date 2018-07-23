package com.github.ytjojo.databind.model;

import com.github.ytjojo.databind.R;
import com.ytjojo.databind.annotation.BindingViewModel;

/**
 * Created by jiulongteng on 2018/7/23.
 */
@BindingViewModel(layoutId = R.layout.layout_bind_info, gegerateClassName = "InfoBinding1")
public class Info {

    public String name;
    private  String type;

    public InfoItem item;

}
