package com.github.ytjojo.databind.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

import com.github.ytjojo.databind.BR;


/**
 * Created by jiulongteng on 2018/6/25.
 */

public class User extends BaseObservable {

    private String userName;

    private String firstName;
    private String lastName;


    @Bindable
    public String getUserName(){
        return this.userName;
    }

    public void setUserName(String userName){
        this.userName = userName;
        notifyPropertyChanged(BR.userName);

    }
    @Bindable
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        notifyPropertyChanged(BR.firstName);
    }
    @Bindable
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        notifyPropertyChanged(BR.lastName);
    }
}
