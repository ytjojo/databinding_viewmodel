package com.ytjojo.binding.plugin;

import android.databinding.tool.processing.Scope;
import android.databinding.tool.util.L;

import com.ytjojo.databinding.compiler.tool.LayoutXmlProcessor;
import com.ytjojo.databinding.compiler.tool.util.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

/**
 * Task that parses xml files and generated metadata.
 * Will be removed when aapt supports binding tags.
 */
public class DataBindingProcessLayoutsTask extends DefaultTask {

    private LayoutXmlProcessor xmlProcessor;

    private File sdkDir;

    private File xmlOutFolder;


    @TaskAction
    public void processResources()
            throws ParserConfigurationException, SAXException, XPathExpressionException,
            IOException {
        Log.i("running process layouts task %s", getName());
        xmlProcessor.processResources();
        try {
            writeLayoutXmls();
        } catch (JAXBException e) {
            // gradle sometimes fails to resolve JAXBException.
            // We get stack trace manually to ensure we have the log
            Logger.get().error( "cannot write layout xmls");
        }
        Scope.assertNoError();
    }

    public void writeLayoutXmls() throws JAXBException {
        xmlProcessor.writeLayoutInfoFiles(xmlOutFolder);
    }

    public LayoutXmlProcessor getXmlProcessor() {
        return xmlProcessor;
    }

    public void setXmlProcessor(LayoutXmlProcessor xmlProcessor) {
        this.xmlProcessor = xmlProcessor;
    }

    public File getSdkDir() {
        return sdkDir;
    }

    public void setSdkDir(File sdkDir) {
        this.sdkDir = sdkDir;
    }

    public File getXmlOutFolder() {
        return xmlOutFolder;
    }

    public void setXmlOutFolder(File xmlOutFolder) {
        this.xmlOutFolder = xmlOutFolder;
    }

}