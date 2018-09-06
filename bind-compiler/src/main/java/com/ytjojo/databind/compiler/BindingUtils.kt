package com.ytjojo.databind.compiler

import com.databinding.tool.ext.androidId
import com.databinding.tool.ext.stripNonJava

/**
 * Created by jiulongteng on 2018/8/21.
 */

fun idToField(ids: String):String{
    return ids.androidId().stripNonJava()
}