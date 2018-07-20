package com.github.ytjojo.article;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

/**
 * Created by jiulongteng on 2018/6/25.
 */

public class Ariticle extends BaseObservable {

    private String title;

    private String createDate;
    private String desc;
    private String authorName;
    @Bindable
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    @Bindable
    public String getCreateDate() {
        return createDate;
    }

    public void setCreateDate(String createDate) {
        this.createDate = createDate;
    }
    @Bindable
    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
    @Bindable
    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }
}
