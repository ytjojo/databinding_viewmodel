package com.ytjojo.databinding.compiler.tool;

import com.databinding.tool.writer.JavaFileWriter;
import com.ytjojo.databinding.compiler.tool.store.LayoutFileParser;
import com.ytjojo.databinding.compiler.tool.store.ResourceBundle;

import org.xml.sax.SAXException;

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

        for( File file : resources){
            if(!file.exists()){
                continue;
            }
            if(file.isDirectory()){
                File[] files = file.listFiles(layoutFolderFilter);
                if(files != null && files.length >0){
                    for(File dir: files){
                       File[] xmlFiles = dir.listFiles(xmlFileFilter);
                        for(File layoutFile: xmlFiles){
                            result.add(layoutFile);
                        }
                    }
                }
            }else {
                if(xmlFileFilter.accept(file.getParentFile(), file.getName())){
                    result.add(file);
                }

            }
        }
        return result;
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
//        (name = new StringBuilder(layout.getFileName())).append('-').append(layout.getDirectory());
//
//        for(int i = name.length() - 1; i >= 0; --i) {
//            if (name.charAt(i) == '-') {
//                name.deleteCharAt(i);
//                char c = Character.toUpperCase(name.charAt(i));
//                name.setCharAt(i, c);
//            }
//        }

        name = new StringBuilder(layout.getFileName());


        return name.toString();
    }



}