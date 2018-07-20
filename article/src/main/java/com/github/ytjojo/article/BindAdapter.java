package com.github.ytjojo.article;

import android.content.res.AssetManager;
import android.databinding.BindingAdapter;
import android.databinding.BindingConversion;
import android.databinding.adapters.ListenerUtil;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by jiulongteng on 2018/6/25.
 */

public class BindAdapter {

    @BindingAdapter("android:paddingLeft")
    public static void setPaddingLeft(View view, int padding) {
        view.setPadding(padding,
                view.getPaddingTop(),
                view.getPaddingRight(),
                view.getPaddingBottom());
    }

    @BindingAdapter({"imageUrl", "error"})
    public static void loadImage(ImageView view, String url, Drawable error) {
//        Glide.with(view.getContext()).load(url).error(error).into(view);
        Glide.with(view.getContext()).load(url).apply(new RequestOptions().override(100)
                .centerCrop().placeholder(error)
                .error(error)).into(view);
    }

    @BindingAdapter("android:onLayoutChange")
    public static void setOnLayoutChangeListener(View view, View.OnLayoutChangeListener oldValue,
                                                 View.OnLayoutChangeListener newValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (oldValue != null) {
                view.removeOnLayoutChangeListener(oldValue);
            }
            if (newValue != null) {
                view.addOnLayoutChangeListener(newValue);
            }
        }
    }

    @BindingAdapter("android:onViewAttachedToWindow")
    public static void setListener(View view, OnViewAttachedToWindow attached) {
        setListener(view, null, attached);
    }

    @BindingAdapter("android:onViewDetachedFromWindow")
    public static void setListener(View view, OnViewDetachedFromWindow detached) {
        setListener(view, detached, null);
    }

    @BindingAdapter({"android:onViewDetachedFromWindow", "android:onViewAttachedToWindow"})
    public static void setListener(View view, final OnViewDetachedFromWindow detach,
                                   final OnViewAttachedToWindow attach) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            final View.OnAttachStateChangeListener newListener;
            if (detach == null && attach == null) {
                newListener = null;
            } else {
                newListener = new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        if (attach != null) {
                            attach.onViewAttachedToWindow(v);
                        }
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        if (detach != null) {
                            detach.onViewDetachedFromWindow(v);
                        }
                    }
                };
            }
            final View.OnAttachStateChangeListener oldListener = ListenerUtil.trackListener(view,
                    newListener, R.id.onAttachStateChangeListener);
            if (oldListener != null) {
                view.removeOnAttachStateChangeListener(oldListener);
            }
            if (newListener != null) {
                view.addOnAttachStateChangeListener(newListener);
            }
        }
    }

    @BindingConversion
    public static String convertDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
        return sdf.format(date);
    }
    @BindingConversion
    public static Drawable convertColorToDrawable(int color) {
        return new ColorDrawable(color);
    }

    @BindingAdapter({"font"})
    public static void setFont(TextView textView, String fontName){
        AssetManager assetManager = textView.getContext().getAssets();
        String path = "fonts/" + fontName;
        if(sCache == null){
            sCache = new HashMap<>();
        }
        Typeface typeface = sCache.get(path);
        if (typeface == null) {
            typeface = Typeface.createFromAsset(assetManager, path);
            sCache.put(path, typeface);
        }
        textView.setTypeface(typeface);
    }
    private static HashMap<String,Typeface> sCache;

    @BindingAdapter({"names"})
    public static void setNames(ViewGroup linearLayout, ArrayList<String> names) {

        Object o =linearLayout.getTag(111);
        ArrayList<TextView> childs = null;
        if(o == null){
            childs = new ArrayList<>();

        }else {
            childs = (ArrayList<TextView>) o;
        }
        if(linearLayout.getChildCount() >0){
            for (int i = 0; i < linearLayout.getChildCount(); i++) {
                if(!childs.contains(linearLayout.getChildAt(0))){
                    childs.add((TextView) linearLayout.getChildAt(i));
                }
            }
            linearLayout.removeAllViews();
        }

        for (String s : names) {
            if(childs.size() >0){
               TextView t = childs.remove(0);
                t.setText(s);
                linearLayout.addView(t);
            }else {
                TextView t = new TextView(linearLayout.getContext());
                t.setText(s);
                linearLayout.addView(t);
            }

        }
        linearLayout.setTag(111,childs);
    }
}


