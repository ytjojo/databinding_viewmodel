package com.ytjojo.binding.plugin;


import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.api.LibraryVariantImpl;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.JavaCompileOptions;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.factory.AndroidJavaCompile;
import com.android.builder.model.SourceProvider;
import com.databinding.tool.util.L;
import com.databinding.tool.writer.JavaFileWriter;
import com.ytjojo.databinding.compiler.tool.LayoutXmlProcessor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.compile.JavaCompile;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;

import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;

public class BindingPlugin implements Plugin<Project> {
    private Logger logger;
    @Override
    public void apply(Project project) {
        logger = project.getLogger();
        Log.init(project);
//        project.getTasks().withType(JavaCompile.class).whenTaskAdded(new Action<JavaCompile>() {
//            @Override
//            public void execute(JavaCompile javaCompile) {
//                final File xmlOutDir = new File(project.getBuildDir() + "/layout-info/" +
//                        "debug");
//                javaCompile.getOptions().getCompilerArgs().add("-AGradleVariantConfiguration_DirName="+xmlOutDir.getAbsolutePath());
//            }
//        });
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                try {
                    createXmlProcessor(project);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });
        project.beforeEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {

            }
        });


    }


    private void createXmlProcessor(Project project)
            throws NoSuchFieldException, IllegalAccessException {
        L.d("creating xml processor for " + project);
        Object androidExt = project.getExtensions().getByName("android");
        if (!(androidExt instanceof BaseExtension)) {
            return;
        }
        if (androidExt instanceof AppExtension) {
            createXmlProcessorForApp(project, (AppExtension) androidExt);
        } else if (androidExt instanceof LibraryExtension) {
            createXmlProcessorForLibrary(project, (LibraryExtension) androidExt);
        } else {
            logE(new UnsupportedOperationException("cannot understand android ext"),
                    "unsupported android extension. What is it? %s", androidExt);
        }
    }

    private void createXmlProcessorForLibrary(Project project, LibraryExtension lib)
            throws NoSuchFieldException, IllegalAccessException {
        File sdkDir = lib.getSdkDirectory();
        L.d("create xml processor for " + lib);
//        for (TestVariant variant : lib.getTestVariants()) {
//            logD("test variant %s. dir name %s", variant, variant.getDirName());
//            BaseVariantData variantData = getVariantData(variant);
//            attachXmlProcessor(project, variantData, sdkDir, false,variant);//tests extend apk variant
//        }
        for (LibraryVariant variant : lib.getLibraryVariants()) {
            logD("library variant %s. dir name %s", variant, variant.getDirName());
            BaseVariantData variantData = getVariantData(variant);
            AndroidJavaCompile task = (AndroidJavaCompile) variant.getJavaCompiler();

            addArgs(project,task,variantData);
            attachXmlProcessor(project, variantData, sdkDir, true,variant,task);
        }
    }

    private void createXmlProcessorForApp(Project project, AppExtension appExt)
            throws NoSuchFieldException, IllegalAccessException {
        L.d("create xml processor for " + appExt);
        File sdkDir = appExt.getSdkDirectory();
//        for (TestVariant testVariant : appExt.getTestVariants()) {
//            TestVariantData variantData = getVariantData(testVariant);
//            attachXmlProcessor(project, variantData, sdkDir, false,testVariant);
//        }
        for (ApplicationVariant appVariant : appExt.getApplicationVariants()) {
            ApplicationVariantData variantData = getVariantData(appVariant);

            AndroidJavaCompile task = (AndroidJavaCompile) appVariant.getJavaCompiler();

            addArgs(project,task,variantData);

            attachXmlProcessor(project, variantData, sdkDir, false,appVariant,task);
        }
    }
    private void addArgs(Project project,AndroidJavaCompile task,BaseVariantData variantData){
        final File xmlOutDir = new File(project.getBuildDir() + "/layout-info/" +
                variantData.getVariantConfiguration().getDirName());
        task.getOptions().getCompilerArgs().add("-AGradleVariantConfiguration_DirName="+xmlOutDir.getAbsolutePath());

    }


    private Map<String,String> getOptionArgs( BaseVariantData variantData){
        try {
            GradleVariantConfiguration configuration= variantData.getVariantConfiguration();
            Field field =configuration.getClass().getDeclaredField("mergedJavaCompileOptions");
            field.setAccessible(true);
            Object compileOptions =  field.get(configuration);
            Method get= compileOptions.getClass().getMethod("getAnnotationProcessorOptions");
            get.setAccessible(true);
            Object annotationProcessorOptions= get.invoke(compileOptions);
            Method getArguments =  annotationProcessorOptions.getClass().getMethod("getArguments");
            getArguments.setAccessible(true);
            Map<String,String> args= (Map<String, String>) getArguments.invoke(annotationProcessorOptions);

           return args;


        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }
    private LibraryVariantData getVariantData(LibraryVariant variant)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = LibraryVariantImpl.class.getDeclaredField("variantData");
        field.setAccessible(true);
        return (LibraryVariantData) field.get(variant);
    }

    private TestVariantData getVariantData(TestVariant variant)
            throws IllegalAccessException, NoSuchFieldException {
        Field field = TestVariantImpl.class.getDeclaredField("variantData");
        field.setAccessible(true);
        return (TestVariantData) field.get(variant);
    }

    private ApplicationVariantData getVariantData(ApplicationVariant variant)
            throws IllegalAccessException, NoSuchFieldException {
        Field field = ApplicationVariantImpl.class.getDeclaredField("variantData");
        field.setAccessible(true);
        return (ApplicationVariantData) field.get(variant);
    }



    private void attachXmlProcessor(Project project, final BaseVariantData variantData,
                                    final File sdkDir,
                                    final Boolean isLibrary,BaseVariant variant
    ,AndroidJavaCompile javaCompile) {
        final GradleVariantConfiguration configuration = variantData.getVariantConfiguration();
        String applicationId = configuration.getApplicationId();

        final String packageName  = getPackageName(variant);
        List<File> resourceFolders = Arrays.asList(variantData.mergeResourcesTask.getOutputDir());

        final File codeGenTargetFolder = new File(project.getBuildDir() + "/data-binding-info/" +
                configuration.getDirName());
        String writerOutBase = codeGenTargetFolder.getAbsolutePath();
        JavaFileWriter fileWriter = new JavaFileWriter() {
            @Override
            public void writeToFile(String canonicalName, String contents) {

            }

            @Override
            public void deleteFile(String canonicalName) {

            }
        };
        final LayoutXmlProcessor xmlProcessor = new LayoutXmlProcessor(packageName, resourceFolders,
                fileWriter,14, isLibrary);
        final MergeResources processResTask =  variantData.mergeResourcesTask;;
        final File xmlOutDir = new File(project.getBuildDir() + "/layout-info/" +
                configuration.getDirName());
        javaCompile.getOptions().getCompilerArgs().add("-Abinding_modulePackage="+packageName);

        String layoutTaskName = "dataBindingLayouts" + StringUtils
                .capitalize(processResTask.getName());


        final DataBindingProcessLayoutsTask[] processLayoutsTasks
                = new DataBindingProcessLayoutsTask[1];
        project.getTasks().create(layoutTaskName,
                DataBindingProcessLayoutsTask.class,
                new Action<DataBindingProcessLayoutsTask>() {
                    @Override
                    public void execute(final DataBindingProcessLayoutsTask task) {
                        processLayoutsTasks[0] = task;
                        task.setXmlProcessor(xmlProcessor);
                        task.setSdkDir(sdkDir);
                        task.setXmlOutFolder(xmlOutDir);

                        logD("TASK adding dependency on %s for %s", task, processResTask);
                        processResTask.dependsOn(task);
                        processResTask.getInputs().dir(xmlOutDir);
                        for (Object dep : processResTask.getDependsOn()) {
                            if (dep == task) {
                                continue;
                            }
                            logD("adding dependency on %s for %s", dep, task);
                            task.dependsOn(dep);
                        }
                        processResTask.doLast(new Action<Task>() {
                            @Override
                            public void execute(Task unused) {
                                try {
                                    task.writeLayoutXmls();
                                } catch (JAXBException e) {
                                    // gradle sometimes fails to resolve JAXBException.
                                    // We get stack trace manually to ensure we have the log
                                    logE(e, "cannot write layout xmls %s",
                                            ExceptionUtils.getStackTrace(e));
                                }
                            }
                        });
                    }
                });
//        final DataBindingProcessLayoutsTask processLayoutsTask = processLayoutsTasks[0];
//        String packageJarTaskName = "package" + StringUtils.capitalize(fullName) + "Jar";
//        final Task packageTask = project.getTasks().findByName(packageJarTaskName);
//        if (packageTask instanceof Jar) {
//            String removeGeneratedTaskName = "dataBindingExcludeGeneratedFrom" +
//                    StringUtils.capitalize(packageTask.getName());
//            if (project.getTasks().findByName(removeGeneratedTaskName) == null) {
//                final AbstractCompile javaCompileTask = variantData.javacTask;
//                Preconditions.checkNotNull(javaCompileTask);
//
//                project.getTasks().create(removeGeneratedTaskName,
//                        DataBindingExcludeGeneratedTask.class,
//                        new Action<DataBindingExcludeGeneratedTask>() {
//                            @Override
//                            public void execute(DataBindingExcludeGeneratedTask task) {
//                                packageTask.dependsOn(task);
//                                task.dependsOn(javaCompileTask);
//                                task.setAppPackage(packageName);
//                                task.setPackageTask((Jar) packageTask);
//                                task.setLibrary(isLibrary);
//                                task.setGeneratedClassListFile(generatedClassListOut);
//                            }
//                        });
//            }
//        }
    }

    // Parse the variant's main manifest file in order to get the package id which is used to create
    // R.java in the right place.
    private String getPackageName(BaseVariant variant) {
        XmlSlurper slurper = null;
        try {
            slurper = new XmlSlurper(false, false);
            ArrayList<File> list = new ArrayList();
            variant.getSourceSets().stream().forEach(new Consumer<SourceProvider>() {
                @Override
                public void accept(SourceProvider sourceProvider) {
                    list.add(sourceProvider.getManifestFile());
                }
            });

            // According to the documentation, the earlier files in the list are meant to be overridden by the later ones.
            // So the first file in the sourceSets list should be main.
            GPathResult result = null;
            try {
                result = slurper.parse(list.get(0));
            } catch (IOException e) {
                e.printStackTrace();
            }

            return result.getProperty("@package").toString();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return null;
    }
    private void logD(String s, Object... args) {
        logger.info(formatLog(s, args));
    }

    private void logE(Throwable t, String s, Object... args) {
        logger.error(formatLog(s, args), t);
    }

    private String formatLog(String s, Object... args) {
        return "[data binding plugin]: " + String.format(s, args);
    }
}
