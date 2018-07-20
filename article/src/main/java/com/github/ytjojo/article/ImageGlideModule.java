package com.github.ytjojo.article;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

/**
 * 你不必去重写 AppGlideModule 中的任何一个方法。子类中完全可以不用写任何东西，它只需要继承 AppGlideModule 并且添加 @GlideModule 注解。
 * <p>
 * AppGlideModule 的实现必须使用 @GlideModule 注解标记。如果注解不存在，该 module 将不会被 Glide 发现，并且在日志中收到一条带有 Glide tag 的警告，表示 module 未找到
 * <p>
 * Created by xgbxm on 2017/9/27.
 */
@GlideModule
public class ImageGlideModule extends AppGlideModule {
}