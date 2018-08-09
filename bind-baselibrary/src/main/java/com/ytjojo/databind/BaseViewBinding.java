package com.ytjojo.databind;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;

/**
 * Created by jiulongteng on 2018/8/7.
 */

public class BaseViewBinding extends AndroidViewModel {
    public BaseViewBinding(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
