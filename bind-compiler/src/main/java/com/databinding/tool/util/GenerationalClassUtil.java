/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.databinding.tool.util;

import android.support.annotation.Nullable;

import com.databinding.annotationprocessor.ProcessExpressions;
import com.databinding.tool.Context;
import com.databinding.tool.DataBindingBuilder;
import com.databinding.tool.DataBindingCompilerArgs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class that helps adding build specific objects to the jar file
 * and their extraction later on.
 */
public class GenerationalClassUtil {
    private List[] mCache = null;

    @Nullable
    private List<File> mInputDirs;

    @Nullable
    private File mIncrementalOutDir;

    private ExtensionFilter[] mEnabledExtensions;

    public static GenerationalClassUtil create(DataBindingCompilerArgs args) {
        return new GenerationalClassUtil(args);
    }

    private GenerationalClassUtil(DataBindingCompilerArgs args) {
        if (args.isEnableV2()) {
            mEnabledExtensions = new ExtensionFilter[]{ExtensionFilter.BR,
            ExtensionFilter.SETTER_STORE};
        } else {
            mEnabledExtensions = new ExtensionFilter[]{ExtensionFilter.BR, ExtensionFilter.LAYOUT,
                    ExtensionFilter.SETTER_STORE};
        }
        if (StringUtils.isNotBlank(args.getAarOutFolder())) {
            mIncrementalOutDir = new File(args.getAarOutFolder(),
                    DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR);
        } else {
            mIncrementalOutDir = null;
        }
        mInputDirs = new ArrayList<>();
        if (StringUtils.isNotBlank(args.getBuildFolder())) {
            mInputDirs.add(new File(args.getBuildFolder(),
                    DataBindingBuilder.ARTIFACT_FILES_DIR_FROM_LIBS));
        }
    }

    public static GenerationalClassUtil get() {
        return Context.getGenerationalClassUtil();
    }

    public <T extends Serializable> List<T> loadObjects(ExtensionFilter filter) {
        if (mCache == null) {
            buildCache();
        }
        List result = mCache[filter.ordinal()];
        Preconditions.checkNotNull(result, "Invalid filter " + filter);
        //noinspection unchecked
        return result;
    }

    private void buildCache() {
        L.d("building generational class cache");

        mCache = new List[ExtensionFilter.values().length];
        for (ExtensionFilter filter : mEnabledExtensions) {
            mCache[filter.ordinal()] = new ArrayList();
        }
        loadFromBuildInfo();
    }

    /**
     * To compile with jack, included jars are converted to an intermediate format which does not
     * keep .bin files.
     * <p>
     * To workaround this issue, we have a gradle transform that extracts bin files from jars into
     * a folder before they are sent to jack. The processor later checks the folder and parses
     * the .bin files.
     * <p>
     * This is a backward compatibility measure and should eventually be phased out after we move
     * to an aar based information retrieval model.
     */
    private void loadFromBuildInfo() {
        mInputDirs.forEach(this::loadFromDirectory);
    }

    private void loadFromDirectory(File directory) {
        if (directory == null || !directory.canRead() || !directory.isDirectory()) {
            return;
        }
        for (File file : FileUtils.listFiles(directory, TrueFileFilter.INSTANCE,
                TrueFileFilter.INSTANCE)) {
            for (ExtensionFilter filter : mEnabledExtensions) {
                if (filter.accept(file.getName())) {
                    InputStream inputStream = null;
                    try {
                        inputStream = FileUtils.openInputStream(file);
                        Serializable item = fromInputStream(inputStream);
                        if (item != null) {
                            //noinspection unchecked
                            mCache[filter.ordinal()].add(item);
                            L.d("loaded item %s from file", item);
                        }
                    } catch (IOException e) {
                        L.e(e, "Could not merge in Bindables from %s", file.getAbsolutePath());
                    } catch (ClassNotFoundException e) {
                        L.e(e, "Could not read Binding properties intermediate file. %s",
                                file.getAbsolutePath());
                    } finally {
                        IOUtils.closeQuietly(inputStream);
                    }
                }
            }
        }
    }

    private Serializable fromInputStream(InputStream inputStream)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in = new IgnoreSerialIdObjectInputStream(inputStream);
        return (Serializable) in.readObject();

    }

    public void writeIntermediateFile(String packageName, String fileName,
            Serializable object) {
        ObjectOutputStream oos = null;
        OutputStream ios = null;
        try {
            try {
                Preconditions.checkNotNull(mIncrementalOutDir, "incremental out directory should be"
                        + " set to aar output directory.");
                //noinspection ResultOfMethodCallIgnored
                mIncrementalOutDir.mkdirs();
                File out = new File(mIncrementalOutDir, packageName + "-" + fileName);
                ios = new FileOutputStream(out);
                oos = new ObjectOutputStream(ios);
                oos.writeObject(object);
                oos.close();
                L.d("wrote intermediate bindable file %s %s", packageName, fileName);
            } catch (IOException e) {
                L.e(e, "Could not write to intermediate file: %s", fileName);
            } finally {
                IOUtils.closeQuietly(oos);
                IOUtils.closeQuietly(ios);
            }
        } catch (LoggedErrorException e) {
            // This will be logged later, so don't worry about it
        }
    }

    public enum ExtensionFilter {
        BR("-br.bin"),
        LAYOUT("-layoutinfo.bin"),
        SETTER_STORE("-setter_store.bin");
        private final String mExtension;
        ExtensionFilter(String extension) {
            mExtension = extension;
        }

        public boolean accept(String entryName) {
            return entryName.endsWith(mExtension);
        }

        public String getExtension() {
            return mExtension;
        }
    }

    private static class IgnoreSerialIdObjectInputStream extends ObjectInputStream {

        public IgnoreSerialIdObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected ObjectStreamClass readClassDescriptor()
                throws IOException, ClassNotFoundException {
            ObjectStreamClass original = super.readClassDescriptor();
            // hack for https://issuetracker.google.com/issues/71057619
            if (ProcessExpressions.IntermediateV1.class.getName().equals(original.getName())) {
                return ObjectStreamClass.lookup(ProcessExpressions.IntermediateV1.class);
            }
            return original;
        }
    }
}
