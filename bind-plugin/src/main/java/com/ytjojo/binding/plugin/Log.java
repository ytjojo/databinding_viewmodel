package com.ytjojo.binding.plugin;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Created by jiulongteng on 2018/8/7.
 */

public class Log {
    static Logger logger;
    public static void init(Project project){
       logger=  project.getLogger();
    }
    public static void d(String s,Object args){
        logger.debug(s,args);
    }

    public static void d(String s){
        logger.debug(s);
    }

    public static void i(String s,Object args){
        logger.info(s,args);
    }
    public static void i(String s){
        logger.info(s);
    }

    public static void e(String s,Object args){
        logger.error(s,args);
    }
    public static void e(String s){
        logger.error(s);
    }
}
