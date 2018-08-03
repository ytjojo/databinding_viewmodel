package com.ytjojo.binding.plugin.utils

import org.gradle.api.GradleException
import org.gradle.api.Project


public class PackegeUtil{


    /**
     * R.java包名兼容获取
     */
    String packageForRCompat(org.gradle.api.Project project,def processAndroidResourceTask) {
        if (processAndroidResourceTask == null) {
            return null
        }
        String packageForR = null
        try {
            packageForR = processAndroidResourceTask.getPackageForR()
        } catch (Exception e) {
            project.logger.info(e.getMessage())
        }
        if (packageForR == null) {
            try {
                packageForR = processAndroidResourceTask.getOriginalApplicationId()
            } catch (Exception e) {
                project.logger.info(e.getMessage())
            }
        }
        return packageForR
    }

    String getPackageForRCompatCompat(Project project,String variantName) {
        if (variantName == null || variantName.length() == 0) {
            throw new GradleException("variantName 不能为空，且必须是驼峰形式")
        }
        def processAndroidResourceTask = project.tasks.findByName("process${variantName.capitalize()}Resources")
        String packageForR = packageForRCompat(project,processAndroidResourceTask)
        if (packageForR == null) {
            return project.android.defaultConfig.applicationId
        }
        return packageForR
    }

}