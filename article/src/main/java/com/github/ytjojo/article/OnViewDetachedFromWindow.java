package com.github.ytjojo.article;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public interface OnViewDetachedFromWindow {
        void onViewDetachedFromWindow(View v);
}