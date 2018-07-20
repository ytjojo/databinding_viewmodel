package com.github.ytjojo.article;

import android.app.Application;
import android.arch.core.util.Function;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.support.annotation.NonNull;


/**
 * Created by jiulongteng on 2018/6/28.
 */

public class ArticleViewModel extends AndroidViewModel {
    public ObservableField<String> tag=new ObservableField<>();
    public ObservableField<String> name=new ObservableField<>();
    public ObservableBoolean show =new ObservableBoolean();
    public ObservableField<String> type =new ObservableField<>();

    public ArticleViewModel(@NonNull Application application) {
        super(application);
//        Transformations.map(tag, new Function<String, String>() {
//            @Override
//            public String apply(String input) {
//                return input +" sssss";
//            }
//        });
//        Transformations.switchMap(tag, new Function<String, LiveData<String>>() {
//            @Override
//            public LiveData<String> apply(String input) {
//                MutableLiveData<String> out = new MutableLiveData<String>();
//                 out.setValue(input);
//                return out;
//            }
//        });
    }

    public void onClick(){

    }

}
