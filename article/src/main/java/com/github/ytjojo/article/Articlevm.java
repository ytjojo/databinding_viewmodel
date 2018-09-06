package com.github.ytjojo.article;

import android.databinding.Observable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;
import android.databinding.adapters.TextViewBindingAdapter;
import android.view.View;

/**
 * Created by jiulongteng on 2018/6/29.
 */

public class Articlevm {
    public final ObservableBoolean visable = new ObservableBoolean();
    public final ObservableInt count = new ObservableInt();
    public final ObservableField<String> title =new ObservableField<>();

    public ObservableInt id = new ObservableInt();
    public ObservableField<String> name = new ObservableField<String>();
    public ObservableField<String> type = new ObservableField<String>();
    public ObservableBoolean checked= new ObservableBoolean();

    public void onClick(){

    }

    public TextViewBindingAdapter.BeforeTextChanged beforeTextChanged = new TextViewBindingAdapter.BeforeTextChanged() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }
    };

    public TextViewBindingAdapter.BeforeTextChanged getBeforeTextChanged(){
        return beforeTextChanged;
    }

    public void before(CharSequence s,int start,int count,int after){

    }
    public String address(){
        return "";
    }


}
