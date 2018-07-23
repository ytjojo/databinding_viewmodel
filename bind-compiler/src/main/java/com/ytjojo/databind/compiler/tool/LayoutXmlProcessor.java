package com.ytjojo.databind.compiler.tool;

import android.databinding.tool.writer.JavaFileWriter;

import com.ytjojo.databind.compiler.tool.store.LayoutFileParser;
import com.ytjojo.databind.compiler.tool.store.ResourceBundle;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

public class LayoutXmlProcessor {
    private final JavaFileWriter mFileWriter;
    private final ResourceBundle mResourceBundle;
    private final int mMinSdk;
    private boolean mProcessingComplete;
    private boolean mWritten;
    private final boolean mIsLibrary;
    private final String mBuildId = UUID.randomUUID().toString();
    private final List<File> mResources;
    private final HashMap<String,File> mNameLayoutsMap;
    private static final FilenameFilter layoutFolderFilter = new FilenameFilter() {
        public final boolean accept(File dir, String name) {
            return name.startsWith("layout");
        }
    };
    private static final FilenameFilter xmlFileFilter = new FilenameFilter() {
        public final boolean accept(File dir, String name) {
            return name.toLowerCase().endsWith(".xml");
        }
    };

    public LayoutXmlProcessor(String applicationPackage, List<File> resources, JavaFileWriter fileWriter, int minSdk, boolean isLibrary) {
        this.mFileWriter = fileWriter;
        this.mResourceBundle = new ResourceBundle(applicationPackage);
        this.mResources = resources;
        this.mMinSdk = minSdk;
        this.mIsLibrary = isLibrary;
        this.mNameLayoutsMap= new HashMap<>();
    }

    public static List<File> getLayoutFiles(List<File> resources) {
        List<File> result = new ArrayList();
        Iterator i$ = resources.iterator();

        while(true) {
            while(true) {
                File resource;
                do {
                    do {
                        if (!i$.hasNext()) {
                            return result;
                        }
                    } while(!(resource = (File)i$.next()).exists());
                } while(!resource.canRead());

                if (resource.isDirectory()) {
                    File[] arr$;
                    int len$ = (arr$ = resource.listFiles(layoutFolderFilter)).length;

                    for(int i = 0; i < len$; ++i) {

                        int len = (arr$ = arr$[i].listFiles(xmlFileFilter)).length;

                        for(int j = 0; j < len; ++j) {
                            File xmlFile = arr$[j];
                            result.add(xmlFile);
                        }
                    }
                } else if (xmlFileFilter.accept(resource.getParentFile(), resource.getName())) {
                    result.add(resource);
                }
            }
        }
    }

    public ResourceBundle getResourceBundle() {
        return this.mResourceBundle;
    }

    public boolean processResources() throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {
        if (this.mProcessingComplete) {
            return false;
        } else {
            LayoutFileParser layoutFileParser = new LayoutFileParser();
            List<File> allLayoutFiles = getLayoutFiles(this.mResources);

            for(File file:allLayoutFiles){
                String name = file.getName().substring(0,file.getName().length()-4);
                mNameLayoutsMap.put( name ,file);
            }
            Iterator i$ = allLayoutFiles.iterator();


            while(i$.hasNext()) {
                File xmlFile = (File)i$.next();
                ResourceBundle.LayoutFileBundle bindingLayout;
                if ((bindingLayout = layoutFileParser.parseXml(xmlFile, this.mResourceBundle.getAppPackage())) != null && !bindingLayout.isEmpty()) {
                    this.mResourceBundle.addLayoutBundle(bindingLayout,true);
                }
            }

            this.mProcessingComplete = true;
            return true;
        }
    }

    public void writeLayoutInfoFiles(File xmlOutDir) throws JAXBException {
        if (!this.mWritten) {
            Marshaller marshaller = JAXBContext.newInstance(ResourceBundle.LayoutFileBundle.class).createMarshaller();
            Iterator i$ = this.mResourceBundle.getLayoutBundles().values().iterator();

            while(i$.hasNext()) {
                Iterator i2$ = ((List)i$.next()).iterator();

                while(i2$.hasNext()) {
                    ResourceBundle.LayoutFileBundle layout = (ResourceBundle.LayoutFileBundle)i2$.next();
                    this.writeXmlFile(xmlOutDir, layout, marshaller);
                }
            }

            this.mWritten = true;
        }
    }

    private void writeXmlFile(File xmlOutDir, ResourceBundle.LayoutFileBundle layout, Marshaller marshaller) throws JAXBException {
        String filename = this.generateExportFileName(layout) + ".xml";
        String xml = this.toXML(layout, marshaller);
        this.mFileWriter.writeToFile(new File(xmlOutDir, filename), xml);
    }


    private String toXML(ResourceBundle.LayoutFileBundle layout, Marshaller marshaller) throws JAXBException {
        StringWriter writer = new StringWriter();
        marshaller.marshal(layout, writer);
        return writer.getBuffer().toString();
    }

    public String generateExportFileName(ResourceBundle.LayoutFileBundle layout) {
        StringBuilder name;
        (name = new StringBuilder(layout.getFileName())).append('-').append(layout.getDirectory());

        for(int i = name.length() - 1; i >= 0; --i) {
            if (name.charAt(i) == '-') {
                name.deleteCharAt(i);
                char c = Character.toUpperCase(name.charAt(i));
                name.setCharAt(i, c);
            }
        }

        return name.toString();
    }



}