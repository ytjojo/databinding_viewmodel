package com.github.ytjojo.article;

import android.view.View;
import android.widget.Toast;

public class MyEventHandler {
        public void onClick(View view) {
            Toast.makeText(view.getContext(), "    sss", Toast.LENGTH_SHORT).show();
        }

        public void getUser(Articlevm articlevm) {
//            Toast.makeText(,"id:" + userInfo.id.get() + ",name:" + userInfo.name.get() + ",type:" +userInfo.type.get() ,Toast.LENGTH_SHORT).show();
        }
    }