package com.github.ytjojo.article;

import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;

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

    public void onClick(){

    }

}
