package com.github.ytjojo.article;

import android.databinding.ViewDataBinding;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by jiulongteng on 2018/6/25.
 */

public class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.BindHolder> {

    @Override
    public BindHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(BindHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    public static class BindHolder extends RecyclerView.ViewHolder{
        ViewDataBinding dataBinding;
        public BindHolder(View itemView) {
            super(itemView);
        }
    }
}
