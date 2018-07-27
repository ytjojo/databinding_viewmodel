/*
 * Copyright (C) 2015 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ytjojo.databind.compiler.tool.store;

import com.databinding.tool.processing.ErrorMessages;
import com.databinding.tool.processing.Scope;
import com.databinding.tool.processing.ScopedException;
import com.databinding.tool.processing.scopes.FileScopeProvider;
import com.databinding.tool.processing.scopes.LocationScopeProvider;
import com.databinding.tool.store.Location;
import com.databinding.tool.util.L;
import com.databinding.tool.util.ParserHelper;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This is a serializable class that can keep the result of parsing layout files.
 */
public class ResourceBundle implements Serializable {
    private static final String[] ANDROID_VIEW_PACKAGE_VIEWS = new String[]
            {"View", "ViewGroup", "ViewStub", "TextureView", "SurfaceView"};
    private String mAppPackage;

    private HashMap<String, List<LayoutFileBundle>> mLayoutBundles
            = new HashMap<String, List<LayoutFileBundle>>();

    private Set<LayoutFileBundle> mLayoutFileBundlesInSource = new HashSet<>();

    private Map<String, String> mDependencyBinders = new HashMap<>();

    private List<File> mRemovedFiles = new ArrayList<File>();

    private boolean mValidated = false;

    public ResourceBundle(String appPackage) {
        mAppPackage = appPackage;
    }

    public void addLayoutBundle(LayoutFileBundle bundle, boolean fromSource) {
        if (bundle.mFileName == null) {
            L.e("File bundle must have a name. %s does not have one.", bundle);
            return;
        }
        if (fromSource) {
            mLayoutFileBundlesInSource.add(bundle);
        }
        if (!mLayoutBundles.containsKey(bundle.mFileName)) {
            mLayoutBundles.put(bundle.mFileName, new ArrayList<>());
        }

        final List<LayoutFileBundle> bundles = mLayoutBundles.get(bundle.mFileName);
        for (LayoutFileBundle existing : bundles) {
            if (existing.equals(bundle)) {
                L.d("skipping layout bundle %s because it already exists.", bundle);
                return;
            }
        }
        L.d("adding bundle %s", bundle);
        bundles.add(bundle);
    }


    public Set<LayoutFileBundle> getLayoutFileBundlesInSource() {
        return mLayoutFileBundlesInSource;
    }

    public HashMap<String, List<LayoutFileBundle>> getLayoutBundles() {
        return mLayoutBundles;
    }

    public String getAppPackage() {
        return mAppPackage;
    }


    public void validateMultiResLayouts() {
        for (List<LayoutFileBundle> layoutFileBundles : mLayoutBundles.values()) {
            for (LayoutFileBundle layoutFileBundle : layoutFileBundles) {
                List<BindingTargetBundle> unboundIncludes = new ArrayList<>();
                for (BindingTargetBundle target : layoutFileBundle.getBindingTargetBundles()) {
                    if (target.isBinder()) {
                        List<LayoutFileBundle> boundTo =
                                mLayoutBundles.get(target.getIncludedLayout());
                        final String targetBinding;
                        if (boundTo != null && !boundTo.isEmpty()) {
                            targetBinding = boundTo.get(0).getFullBindingClass();
                        } else {
                            targetBinding = mDependencyBinders.getOrDefault(
                                    target.getIncludedLayout(), null);
                        }
                        if (targetBinding == null) {
                            L.d("There is no binding for %s, reverting to plain layout",
                                    target.getIncludedLayout());
                            if (target.getId() == null) {
                                unboundIncludes.add(target);
                            } else {
                                target.setInterfaceType("android.view.View");
                                target.mViewName = "android.view.View";
                            }
                        } else {
                            target.setInterfaceType(targetBinding);
                        }
                    }
                }
                layoutFileBundle.getBindingTargetBundles().removeAll(unboundIncludes);
            }
        }

        for (Map.Entry<String, List<LayoutFileBundle>> bundles : mLayoutBundles.entrySet()) {
            if (bundles.getValue().size() < 2) {
                continue;
            }

            // validate all ids are in correct view types
            // and all variables have the same name


            for (LayoutFileBundle bundle : bundles.getValue()) {
                // now add missing ones to each to ensure they can be referenced
                L.d("checking for missing variables in %s / %s", bundle.mFileName,
                        bundle.mConfigName);
            }

            Set<String> includeBindingIds = new HashSet<String>();
            Set<String> viewBindingIds = new HashSet<String>();
            Map<String, String> viewTypes = new HashMap<String, String>();
            Map<String, String> includes = new HashMap<String, String>();
            L.d("validating ids for %s", bundles.getKey());
            Set<String> conflictingIds = new HashSet<String>();
            for (LayoutFileBundle bundle : bundles.getValue()) {
                try {
                    Scope.enter(bundle);
                    for (BindingTargetBundle target : bundle.mBindingTargetBundles) {
                        try {
                            Scope.enter(target);
                            L.d("checking %s %s %s", target.getId(), target.getFullClassName(),
                                    target.isBinder());
                            if (target.mId != null) {
                                if (target.isBinder()) {
                                    if (viewBindingIds.contains(target.mId)) {
                                        L.d("%s is conflicting", target.mId);
                                        conflictingIds.add(target.mId);
                                        continue;
                                    }
                                    includeBindingIds.add(target.mId);
                                } else {
                                    if (includeBindingIds.contains(target.mId)) {
                                        L.d("%s is conflicting", target.mId);
                                        conflictingIds.add(target.mId);
                                        continue;
                                    }
                                    viewBindingIds.add(target.mId);
                                }
                                String existingType = viewTypes.get(target.mId);
                                if (existingType == null) {
                                    L.d("assigning %s as %s", target.getId(),
                                            target.getFullClassName());
                                    viewTypes.put(target.mId, target.getFullClassName());
                                    if (target.isBinder()) {
                                        includes.put(target.mId, target.getIncludedLayout());
                                    }
                                } else if (!existingType.equals(target.getFullClassName())) {
                                    if (target.isBinder()) {
                                        L.d("overriding %s as base binder", target.getId());
                                        viewTypes.put(target.mId,
                                                "android.databinding.ViewDataBinding");
                                        includes.put(target.mId, target.getIncludedLayout());
                                    } else {
                                        L.d("overriding %s as base view", target.getId());
                                        viewTypes.put(target.mId, "android.view.View");
                                    }
                                }
                            }
                        } catch (ScopedException ex) {
                            Scope.defer(ex);
                        } finally {
                            Scope.exit();
                        }
                    }
                } finally {
                    Scope.exit();
                }
            }

            if (!conflictingIds.isEmpty()) {
                for (LayoutFileBundle bundle : bundles.getValue()) {
                    for (BindingTargetBundle target : bundle.mBindingTargetBundles) {
                        if (conflictingIds.contains(target.mId)) {
                            Scope.registerError(String.format(
                                    ErrorMessages.MULTI_CONFIG_ID_USED_AS_IMPORT,
                                    target.mId), bundle, target);
                        }
                    }
                }
            }

            for (LayoutFileBundle bundle : bundles.getValue()) {
                try {
                    Scope.enter(bundle);
                    for (Map.Entry<String, String> viewType : viewTypes.entrySet()) {
                        BindingTargetBundle target = bundle.getBindingTargetById(viewType.getKey());
                        if (target == null) {
                            String include = includes.get(viewType.getKey());
                            if (include == null) {
                                bundle.createBindingTarget(viewType.getKey(), viewType.getValue(),
                                        false, null);
                            } else {
                                BindingTargetBundle bindingTargetBundle = bundle
                                        .createBindingTarget(
                                                viewType.getKey(), null, false, null);
                                bindingTargetBundle
                                        .setIncludedLayout(includes.get(viewType.getKey()));
                                bindingTargetBundle.setInterfaceType(viewType.getValue());
                            }
                        } else {
                            L.d("setting interface type on %s (%s) as %s", target.mId,
                                    target.getFullClassName(), viewType.getValue());
                            target.setInterfaceType(viewType.getValue());
                        }
                    }
                } catch (ScopedException ex) {
                    Scope.defer(ex);
                } finally {
                    Scope.exit();
                }
            }
        }
        // assign class names to each
        for (Map.Entry<String, List<LayoutFileBundle>> entry : mLayoutBundles.entrySet()) {
            for (LayoutFileBundle bundle : entry.getValue()) {
                final String configName;
                final String parentFileName = bundle.mDirectory;
                L.d("parent file for %s is %s", bundle.getFileName(), parentFileName);
                if ("layout".equals(parentFileName)) {
                    configName = "";
                } else {
                    configName = ParserHelper.toClassName(
                            parentFileName.substring("layout-".length()));
                }
                bundle.mConfigName = configName;
            }
        }
    }



    public void addRemovedFile(File file) {
        mRemovedFiles.add(file);
    }

    public List<File> getRemovedFiles() {
        return mRemovedFiles;
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlRootElement(name = "Layout")
    public static class LayoutFileBundle implements Serializable, FileScopeProvider {
        @XmlAttribute(name = "layout", required = true)
        public String mFileName;
        @XmlAttribute(name = "modulePackage", required = true)
        public String mModulePackage;
        @XmlAttribute(name = "absoluteFilePath", required = true)
        public String mAbsoluteFilePath;
        private String mConfigName;

        // The binding class as given by the user
        @XmlAttribute(name = "bindingClass", required = false)
        public String mBindingClass;

        // The full package and class name as determined from mBindingClass and mModulePackage
        private String mFullBindingClass;

        // The simple binding class name as determined from mBindingClass and mModulePackage
        private String mBindingClassName;

        // The package of the binding class as determined from mBindingClass and mModulePackage
        private String mBindingPackage;

        @XmlAttribute(name = "directory", required = true)
        public String mDirectory;

        @XmlElementWrapper(name = "Targets")
        @XmlElement(name = "Target")
        public List<BindingTargetBundle> mBindingTargetBundles =
                new ArrayList<BindingTargetBundle>();

        @XmlAttribute(name = "isMerge", required = true)
        private boolean mIsMerge;

        private LocationScopeProvider mClassNameLocationProvider;

        // for XML binding
        public LayoutFileBundle() {
        }

        /**
         * Updates configuration fields from the given bundle but does not change variables,
         * binding expressions etc.
         */
        public void inheritConfigurationFrom(LayoutFileBundle other) {
            mFileName = other.mFileName;
            mModulePackage = other.mModulePackage;
            mBindingClass = other.mBindingClass;
            mFullBindingClass = other.mFullBindingClass;
            mBindingClassName = other.mBindingClassName;
            mBindingPackage = other.mBindingPackage;
            mIsMerge = other.mIsMerge;
        }

        public LayoutFileBundle(File file, String fileName, String directory,
                                String modulePackage, boolean isMerge) {
            mFileName = fileName;
            mDirectory = directory;
            mModulePackage = modulePackage;
            mIsMerge = isMerge;
            mAbsoluteFilePath = file.getAbsolutePath();
        }


        public BindingTargetBundle createBindingTarget(String id, String viewName,
                                                       boolean used, Location location) {
            BindingTargetBundle target = new BindingTargetBundle(id, viewName, used,location);
            mBindingTargetBundles.add(target);
            return target;
        }


        public BindingTargetBundle getBindingTargetById(String key) {
            for (BindingTargetBundle target : mBindingTargetBundles) {
                if (key.equals(target.mId)) {
                    return target;
                }
            }
            return null;
        }

        public String getFileName() {
            return mFileName;
        }

        public String getConfigName() {
            return mConfigName;
        }

        public String getDirectory() {
            return mDirectory;
        }

        public boolean isEmpty() {
            return mBindingTargetBundles.isEmpty();
        }
        public boolean isMerge() {
            return mIsMerge;
        }

        public String getBindingClassName() {
            if (mBindingClassName == null) {
                String fullClass = getFullBindingClass();
                int dotIndex = fullClass.lastIndexOf('.');
                mBindingClassName = fullClass.substring(dotIndex + 1);
            }
            return mBindingClassName;
        }

        public void setBindingClass(String bindingClass) {
            mBindingClass = bindingClass;
        }

        public String getBindingClassPackage() {
            if (mBindingPackage == null) {
                String fullClass = getFullBindingClass();
                int dotIndex = fullClass.lastIndexOf('.');
                mBindingPackage = fullClass.substring(0, dotIndex);
            }
            return mBindingPackage;
        }

        public String getFullBindingClass() {
            if (mFullBindingClass == null) {
                if (mBindingClass == null) {
                    mFullBindingClass = getModulePackage() + ".databinding." +
                            ParserHelper.toClassName(getFileName()) + "Binding";
                } else if (mBindingClass.startsWith(".")) {
                    mFullBindingClass = getModulePackage() + mBindingClass;
                } else if (mBindingClass.indexOf('.') < 0) {
                    mFullBindingClass = getModulePackage() + ".databinding." + mBindingClass;
                } else {
                    mFullBindingClass = mBindingClass;
                }
            }
            return mFullBindingClass;
        }

        public String createImplClassNameWithConfig() {
            return getBindingClassName() + getConfigName() + "Impl";
        }

        public List<BindingTargetBundle> getBindingTargetBundles() {
            return mBindingTargetBundles;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LayoutFileBundle bundle = (LayoutFileBundle) o;

            if (mConfigName != null ? !mConfigName.equals(bundle.mConfigName)
                    : bundle.mConfigName != null) {
                return false;
            }
            if (mDirectory != null ? !mDirectory.equals(bundle.mDirectory)
                    : bundle.mDirectory != null) {
                return false;
            }
            return !(mFileName != null ? !mFileName.equals(bundle.mFileName)
                    : bundle.mFileName != null);

        }

        @Override
        public int hashCode() {
            int result = mFileName != null ? mFileName.hashCode() : 0;
            result = 31 * result + (mConfigName != null ? mConfigName.hashCode() : 0);
            result = 31 * result + (mDirectory != null ? mDirectory.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "LayoutFileBundle{" +
                    ", mDirectory='" + mDirectory + '\'' +
                    ", mConfigName='" + mConfigName + '\'' +
                    ", mModulePackage='" + mModulePackage + '\'' +
                    ", mFileName='" + mFileName + '\'' +
                    '}';
        }

        public String getModulePackage() {
            return mModulePackage;
        }

        public String getAbsoluteFilePath() {
            return mAbsoluteFilePath;
        }

        @Override
        public String provideScopeFilePath() {
            return mAbsoluteFilePath;
        }

        private static final Marshaller sMarshaller;
        private static final Unmarshaller sUnmarshaller;

        static {
            try {
                JAXBContext context = JAXBContext.newInstance(LayoutFileBundle.class);
                sMarshaller = context.createMarshaller();
                sMarshaller.setProperty(Marshaller.JAXB_ENCODING, "utf-8");
                sUnmarshaller = context.createUnmarshaller();
            } catch (JAXBException e) {
                throw new RuntimeException("Cannot create the xml marshaller", e);
            }
        }

        public String toXML() throws JAXBException {
            StringWriter writer = new StringWriter();
            synchronized (sMarshaller) {
                sMarshaller.marshal(this, writer);
                return writer.getBuffer().toString();
            }
        }

        public static LayoutFileBundle fromXML(InputStream inputStream)
                throws JAXBException {
            synchronized (sUnmarshaller) {
                return (LayoutFileBundle) sUnmarshaller.unmarshal(inputStream);
            }
        }

        public String createTag() {
            return getDirectory() + "/" + getFileName();
        }
    }


    @XmlAccessorType(XmlAccessType.NONE)
    public static class BindingTargetBundle implements Serializable, LocationScopeProvider {
        // public for XML serialization

        @XmlAttribute(name = "id")
        public String mId;
        @XmlAttribute(name = "view", required = false)
        public String mViewName;
        private String mFullClassName;
        public boolean mUsed = true;
        @XmlElementWrapper(name = "Expressions")
        @XmlElement(name = "Expression")
        public List<BindingBundle> mBindingBundleList = new ArrayList<BindingBundle>();
        @XmlAttribute(name = "include")
        public String mIncludedLayout;
        @XmlElement(name = "location")
        public Location mLocation;
        private String mInterfaceType;

        // For XML serialization
        public BindingTargetBundle() {
        }

        public BindingTargetBundle(String id, String viewName, boolean used,Location location
        ) {
            mId = id;
            mViewName = viewName;
            mUsed = used;
            mLocation = location;
        }

        public void addBinding(String name, String expr, boolean isTwoWay, Location location,
                               Location valueLocation) {
            mBindingBundleList.add(
                    new BindingBundle(name, expr, isTwoWay, location, valueLocation));
        }

        public void setIncludedLayout(String includedLayout) {
            mIncludedLayout = includedLayout;
        }

        public String getIncludedLayout() {
            return mIncludedLayout;
        }

        public boolean isBinder() {
            return mIncludedLayout != null && (!"android.view.View".equals(mInterfaceType)
                    || !"android.view.View".equals(mViewName));
        }

        public void setInterfaceType(String interfaceType) {
            mInterfaceType = interfaceType;
        }

        public void setLocation(Location location) {
            mLocation = location;
        }

        public Location getLocation() {
            return mLocation;
        }

        public String getId() {
            return mId;
        }


        public String getFullClassName() {
            if (mFullClassName == null) {
                if (isBinder()) {
                    mFullClassName = mInterfaceType;
                } else if (mViewName.indexOf('.') == -1) {
                    if (Arrays.asList(ANDROID_VIEW_PACKAGE_VIEWS).contains(mViewName)) {
                        mFullClassName = "android.view." + mViewName;
                    } else if ("WebView".equals(mViewName)) {
                        mFullClassName = "android.webkit." + mViewName;
                    } else {
                        mFullClassName = "android.widget." + mViewName;
                    }
                } else {
                    mFullClassName = mViewName;
                }
            }
            if (mFullClassName == null) {
                L.e("Unexpected full class name = null. view = %s, interface = %s, layout = %s",
                        mViewName, mInterfaceType, mIncludedLayout);
            }
            return mFullClassName;
        }

        public boolean isUsed() {
            return mUsed;
        }

        public List<BindingBundle> getBindingBundleList() {
            return mBindingBundleList;
        }

        public String getInterfaceType() {
            return mInterfaceType;
        }

        @Override
        public List<Location> provideScopeLocation() {
            return mLocation == null ? null : Arrays.asList(mLocation);
        }

//        @Override
//        public List<Location> provideScopeLocation() {
//            return mLocation == null ? null : Arrays.asList(mLocation);
//        }

        @XmlAccessorType(XmlAccessType.NONE)
        public static class BindingBundle implements Serializable {

            private String mName;
            private String mExpr;
            private boolean mIsTwoWay;
            private Location mLocation;
            private Location mValueLocation;

            public BindingBundle() {
            }

            public BindingBundle(String name, String expr, boolean isTwoWay, Location location,
                                 Location valueLocation) {
                mName = name;
                mExpr = expr;
                mIsTwoWay = isTwoWay;
                mLocation = location;
                mValueLocation = valueLocation;
            }

            @XmlAttribute(name = "attribute", required = true)
            public String getName() {
                return mName;
            }

            @XmlAttribute(name = "text", required = true)
            public String getExpr() {
                return mExpr;
            }

            public void setName(String name) {
                mName = name;
            }

            public void setExpr(String expr) {
                mExpr = expr;
            }

            public void setTwoWay(boolean isTwoWay) {
                mIsTwoWay = isTwoWay;
            }
//
            @XmlElement(name = "Location")
            public Location getLocation() {
                return mLocation;
            }

            public void setLocation(Location location) {
                mLocation = location;
            }

            @XmlElement(name = "ValueLocation")
            public Location getValueLocation() {
                return mValueLocation;
            }

            @XmlElement(name = "TwoWay")
            public boolean isTwoWay() {
                return mIsTwoWay;
            }

            public void setValueLocation(Location valueLocation) {
                mValueLocation = valueLocation;
            }
        }
    }

}
