package com.github.ytjojo.databind.model;

import com.github.ytjojo.databind.R;
import com.ytjojo.databind.annotation.BindingViewModel;

/**
 * Created by jiulongteng on 2018/7/24.
 */

public interface InterTest {

    @BindingViewModel(layoutId = R.layout.layout_bind_info,bindModelClassName = "com.github.ytjojo.databind.modelTest", generateClassName = "TestBinding")
     void ss();
}
