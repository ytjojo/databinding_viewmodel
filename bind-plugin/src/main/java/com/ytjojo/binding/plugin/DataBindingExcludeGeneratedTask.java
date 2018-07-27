package com.ytjojo.binding.plugin;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

import android.databinding.tool.processing.Scope;
import android.databinding.tool.util.L;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Task to exclude generated classes from the Jar task of a library project
 */
public class DataBindingExcludeGeneratedTask extends DefaultTask {
    private String appPackage;
    private String infoClassQualifiedName;
    @Input
    private File generatedClassListFile;
    private boolean isLibrary;

    private org.gradle.api.tasks.bundling.Jar packageTask;
    private final String EXCLUDE_PATTERN = "android/databinding/layouts/*.*";

    public void setAppPackage(String appPackage) {
        this.appPackage = appPackage;
    }

    public void setInfoClassQualifiedName(String infoClassQualifiedName) {
        this.infoClassQualifiedName = infoClassQualifiedName;
    }

    public void setLibrary(boolean isLibrary) {
        this.isLibrary = isLibrary;
    }

    public void setPackageTask(Jar packageTask) {
        this.packageTask = packageTask;
    }

    public void setGeneratedClassListFile(File generatedClassListFile) {
        this.generatedClassListFile = generatedClassListFile;
    }

    public String getAppPackage() {
        return appPackage;
    }

    public String getInfoClassQualifiedName() {
        return infoClassQualifiedName;
    }

    public File getGeneratedClassListFile() {
        return generatedClassListFile;
    }

    @TaskAction
    public void excludeGenerated() {
        L.d("Excluding generated classes from jar. Is library ? %s", isLibrary);
        String appPkgAsClass = appPackage.replace('.', '/');
        String infoClassAsClass = infoClassQualifiedName.replace('.', '/');
        exclude(infoClassAsClass + ".class");
        exclude(EXCLUDE_PATTERN);
        if (isLibrary) {
            exclude(appPkgAsClass + "/BR.*");
            List<String> generatedClasses = readGeneratedClasses();
            for (String klass : generatedClasses) {
                exclude(klass.replace('.', '/') + ".class");
            }
        }
        Scope.assertNoError();
        L.d("Excluding generated classes from library jar is done.");
    }

    private void exclude(String pattern) {
        L.d("exclude %s", pattern);
        packageTask.exclude(pattern);
    }

    private List<String> readGeneratedClasses() {
        Preconditions.checkNotNull(generatedClassListFile, "Data binding exclude generated task"
                + " is not configured properly");
        Preconditions.checkArgument(generatedClassListFile.exists(),
                "Generated class list does not exist %s", generatedClassListFile.getAbsolutePath());
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(generatedClassListFile);
            return IOUtils.readLines(fis);
        } catch (FileNotFoundException e) {
            L.e(e, "Unable to read generated class list from %s",
                    generatedClassListFile.getAbsoluteFile());
        } catch (IOException e) {
            L.e(e, "Unexpected exception while reading %s",
                    generatedClassListFile.getAbsoluteFile());
        } finally {
            IOUtils.closeQuietly(fis);
        }
        Preconditions.checkState(false, "Could not read data binding generated class list");
        return null;
    }
}