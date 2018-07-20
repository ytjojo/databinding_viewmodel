package com.github.ytjojo.databind.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

/**
 * Created by jiulongteng on 2018/6/25.
 */

public class Patient extends BaseObservable{

    private String patientName;

    private int age;
    private String sex;
    private boolean showAge;

    @Bindable
    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }
    @Bindable
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
    @Bindable
    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    @Bindable
    public boolean isShowAge() {
        return showAge;
    }

    public void setShowAge(boolean showAge) {
        this.showAge = showAge;
    }
}
