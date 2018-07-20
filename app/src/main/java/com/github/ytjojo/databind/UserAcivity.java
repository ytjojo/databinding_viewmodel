package com.github.ytjojo.databind;

import android.databinding.DataBindingUtil;
import android.databinding.Observable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.github.ytjojo.databind.databinding.LayoutUserBinding;
import com.github.ytjojo.databind.model.User;


/**
 * Created by jiulongteng on 2018/6/25.
 */

public class UserAcivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutUserBinding binding=  DataBindingUtil.setContentView(this,R.layout.layout_user);
        User user = new User();
        binding.setUser(user);
        user.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int i) {
                if(i == BR.firstName){

                }
            }
        });

    }
}
