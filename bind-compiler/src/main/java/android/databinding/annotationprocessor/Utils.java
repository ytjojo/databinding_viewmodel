package android.databinding.annotationprocessor;

import java.util.Set;

/**
 * Created by jiulongteng on 2018/7/17.
 */

public class Utils {

    public static Set<String> get(ProcessExpressions.IntermediateV2  v2){
        return v2.mLayoutInfoMap.keySet();
    }
}
