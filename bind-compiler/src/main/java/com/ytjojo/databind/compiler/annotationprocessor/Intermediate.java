package com.ytjojo.databind.compiler.annotationprocessor;

import com.databinding.annotationprocessor.ProcessExpressions;
import com.databinding.tool.store.ResourceBundle;
import com.databinding.tool.util.L;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 * Created by jiulongteng on 2018/8/17.
 */

public class Intermediate implements Serializable {


    transient Unmarshaller mUnmarshaller;

    // name to xml content map
    Map<String, String> mLayoutInfoMap = new HashMap<String, String>();

    public void addEntry(String name, String contents) {
        mLayoutInfoMap.put(name, contents);
    }

    public void appendTo(ResourceBundle resourceBundle) throws
            JAXBException {
        if (mUnmarshaller == null) {
            JAXBContext context = JAXBContext
                    .newInstance(ResourceBundle.LayoutFileBundle.class);
            mUnmarshaller = context.createUnmarshaller();
        }
        for (String content : mLayoutInfoMap.values()) {
            final InputStream is = IOUtils.toInputStream(content);
            try {
                final ResourceBundle.LayoutFileBundle bundle
                        = (ResourceBundle.LayoutFileBundle) mUnmarshaller.unmarshal(is);
                resourceBundle.addLayoutBundle(bundle, true);
                L.d("loaded layout info file %s", bundle);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    public static Intermediate createIntermediateFromLayouts(String layoutInfoFolderPath) {

        final File layoutInfoFolder = new File(layoutInfoFolderPath);
        if (!layoutInfoFolder.isDirectory()) {
            L.d("layout info folder does not exist, skipping for %s", layoutInfoFolderPath);
            return null;
        }
        Intermediate result = new Intermediate();
        for (File layoutFile : layoutInfoFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        })) {
            try {
                result.addEntry(layoutFile.getName(), FileUtils.readFileToString(layoutFile));
            } catch (IOException e) {
                L.e(e, "cannot load layout file information. Try a clean build");
            }
        }

        return result;
    }

    public static ResourceBundle getResourceBundle(String modulePackage){
        ResourceBundle resourceBundle;
        resourceBundle = new ResourceBundle(modulePackage);
        return resourceBundle;
    }
}
