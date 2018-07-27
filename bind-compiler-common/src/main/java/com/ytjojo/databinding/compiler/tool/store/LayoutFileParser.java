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

package com.ytjojo.databinding.compiler.tool.store;

import android.databinding.parser.XMLLexer;
import android.databinding.parser.XMLParser;
import android.databinding.parser.XMLParserBaseVisitor;
import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;
import android.databinding.tool.processing.scopes.FileScopeProvider;
import android.databinding.tool.store.Location;
import android.databinding.tool.util.L;
import android.databinding.tool.util.ParserHelper;
import android.databinding.tool.util.Preconditions;
import android.databinding.tool.util.StringUtils;
import com.google.common.base.Strings;
import com.ytjojo.databinding.compiler.tool.util.XmlEditor;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.commons.io.FileUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Gets the list of XML files and creates a list of
 * {@link ResourceBundle} that can be persistent or converted to
 * LayoutBinder.
 */
public class LayoutFileParser {


    private static final String LAYOUT_PREFIX = "@layout/";
    private static final String XMLNS_SCHEME="http://schemas.databinding.com/bindingattr";
    public static final String XMLNS_NAMESPACE= "databinding";

    public static final String BINDING_ATTR_PREFIX= "databinding:binding_";

    public ResourceBundle.LayoutFileBundle parseXml(final File inputFile,
                                                    String pkg)
            throws ParserConfigurationException, IOException, SAXException,
            XPathExpressionException {
        final String originalFilePath = inputFile.getAbsolutePath();
        try {
            Scope.enter(new FileScopeProvider() {
                @Override
                public String provideScopeFilePath() {
                    return originalFilePath;
                }
            });
            final String encoding = findEncoding(inputFile);
            return parseOriginalXml(inputFile, pkg, encoding);
        } finally {
            Scope.exit();
        }
    }


    private ResourceBundle.LayoutFileBundle parseOriginalXml(final File original, String pkg,
            String encoding) throws IOException {
        try {
            Scope.enter(new FileScopeProvider() {
                @Override
                public String provideScopeFilePath() {
                    return original.getAbsolutePath();
                }
            });
            final String xmlNoExtension = ParserHelper.stripExtension(original.getName());
            FileInputStream fin = new FileInputStream(original);
            InputStreamReader reader = new InputStreamReader(fin, encoding);
            ANTLRInputStream inputStream = new ANTLRInputStream(reader);
            XMLLexer lexer = new XMLLexer(inputStream);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            XMLParser parser = new XMLParser(tokenStream);
            XMLParser.DocumentContext expr = parser.document();
            XMLParser.ElementContext root = expr.element();

            boolean isMerge = "merge".equals(root.elmName.getText());
            if(!isDataBindingLayout(root)){
                return null;
            }

            ResourceBundle.LayoutFileBundle bundle = new ResourceBundle.LayoutFileBundle(original,
                    xmlNoExtension, original.getParentFile().getName(), pkg, isMerge);
            parseExpressions( root, isMerge, bundle);
            return bundle;
        } finally {
            Scope.exit();
        }
    }

    private static boolean isProcessedElement(String name) {
        if (Strings.isNullOrEmpty(name)) {
            return false;
        }
        if ("view".equals(name) || "include".equals(name) || name.indexOf('.') >= 0) {
            return true;
        }
        return !name.toLowerCase().equals(name);
    }

    private void parseExpressions(final XMLParser.ElementContext rootView,
            final boolean isMerge, ResourceBundle.LayoutFileBundle bundle) {
        final List<XMLParser.ElementContext> bindingElements
                = new ArrayList<XMLParser.ElementContext>();
        final List<XMLParser.ElementContext> otherElementsWithIds
                = new ArrayList<XMLParser.ElementContext>();
        rootView.accept(new XMLParserBaseVisitor<Void>() {
            @Override
            public Void visitElement(@NotNull XMLParser.ElementContext ctx) {
                if (filter(ctx)) {
                    bindingElements.add(ctx);
                } else {
                    String name = ctx.elmName.getText();
                    if (isProcessedElement(name) &&
                            attributeMap(ctx).containsKey("android:id")) {
                        otherElementsWithIds.add(ctx);
                    }
                }
                visitChildren(ctx);
                return null;
            }

            private boolean filter(XMLParser.ElementContext ctx) {
                if (isMerge) {
                    // account for XMLParser.ContentContext
                    if (ctx.getParent().getParent() == rootView) {
                        return true;
                    }
                } else if (ctx == rootView) {
                    return true;
                }
                return hasIncludeChild(ctx) || XmlEditor.hasExpressionAttributes(ctx) ||
                        "include".equals(ctx.elmName.getText());
            }

            private boolean hasIncludeChild(XMLParser.ElementContext ctx) {
                for (XMLParser.ElementContext child : XmlEditor.elements(ctx)) {
                    if ("include".equals(child.elmName.getText())) {
                        return true;
                    }
                }
                return false;
            }
        });

        L.d("number of binding nodes %d", bindingElements.size());
        for (XMLParser.ElementContext parent : bindingElements) {
            final Map<String, String> attributes = attributeMap(parent);
            String nodeName = parent.elmName.getText();
            String viewName = null;
            String includedLayoutName = null;
            final String id = attributes.get("android:id");
            if ("include".equals(nodeName)) {
                // get the layout attribute
                final String includeValue = attributes.get("layout");
                if (Strings.isNullOrEmpty(includeValue)) {
                    L.e("%s must include a layout", parent);
                }
                if (!includeValue.startsWith(LAYOUT_PREFIX)) {
                    L.e("included value (%s) must start with %s.",
                            includeValue, LAYOUT_PREFIX);
                }
                // if user is binding something there, there MUST be a layout file to be
                // generated.
                includedLayoutName = includeValue.substring(LAYOUT_PREFIX.length());
                final ParserRuleContext myParentContent = parent.getParent();
                Preconditions.check(myParentContent instanceof XMLParser.ContentContext,
                        "parent of an include tag must be a content context but it is %s",
                        myParentContent.getClass().getCanonicalName());
                final ParserRuleContext grandParent = myParentContent.getParent();
                Preconditions.check(grandParent instanceof XMLParser.ElementContext,
                        "grandparent of an include tag must be an element context but it is %s",
                        grandParent.getClass().getCanonicalName());
                //noinspection SuspiciousMethodCalls
            } else if ("fragment".equals(nodeName)) {
                if (XmlEditor.hasExpressionAttributes(parent)) {
                    L.e("fragments do not support data binding expressions.");
                }
                continue;
            }else if("ViewStub".equals(nodeName)){
                viewName = getViewName(parent);
                final String includeValue = attributes.get("android:layout");
                if (Strings.isNullOrEmpty(includeValue)) {
                    L.e("%s must include a layout", parent);
                }
                if (!includeValue.startsWith(LAYOUT_PREFIX)) {
                    L.e("ViewStup layout value (%s) must start with %s.",
                            includeValue, LAYOUT_PREFIX);
                }
                includedLayoutName = includeValue.substring(LAYOUT_PREFIX.length());

            } else {
                viewName = getViewName(parent);
            }
            final ResourceBundle.BindingTargetBundle bindingTargetBundle =
                    bundle.createBindingTarget(id, viewName, true,
                            new Location(parent));
            bindingTargetBundle.setIncludedLayout(includedLayoutName);

//            for(Map.Entry<String,String> entry:attributes.entrySet()){
//                if(entry.getKey().startsWith("databinding:bind_")){
//                    String value = escapeQuotes(entry.getValue(), true);
//                }
//            }
            for (XMLParser.AttributeContext attr : XmlEditor.expressionAttributes(parent)) {
                String value = escapeQuotes(attr.attrValue.getText(), true);
                final int startIndex = 0;
                final int endIndex = value.length() - 1;
                final String strippedValue = value.substring(startIndex, endIndex);
                Location attrLocation = new Location(attr);
                Location valueLocation = new Location();
                // offset to 0 based
                valueLocation.startLine = attr.attrValue.getLine() - 1;
                valueLocation.startOffset = attr.attrValue.getCharPositionInLine() +
                        attr.attrValue.getText().indexOf(strippedValue);
                valueLocation.endLine = attrLocation.endLine;
                valueLocation.endOffset = attrLocation.endOffset - 1; //
                bindingTargetBundle.addBinding(escapeQuotes(attr.attrName.getText(), false),
                        strippedValue, true, attrLocation, valueLocation);
            }
        }

        for (XMLParser.ElementContext elm : otherElementsWithIds) {
            final String id = attributeMap(elm).get("android:id");
            final String className = getViewName(elm);
            bundle.createBindingTarget(id, className, true, new Location(elm));
        }
    }

    private String getViewName(XMLParser.ElementContext elm) {
        String viewName = elm.elmName.getText();
         if ("include".equals(viewName) && !XmlEditor.hasExpressionAttributes(elm)) {
            viewName = "android.view.View";
        }
        return viewName;
    }

    private boolean isDataBindingLayout(XMLParser.ElementContext root) {
        String viewName = root.elmName.getText();
        if (!"merge".equals(viewName)) {
            String classNode = attributeMap(root).get("xmlns:databinding");
            if (Strings.isNullOrEmpty(classNode)) {
               return false;
            }else {
                return true;
            }
        } else {
            return false;
        }
    }

    private void parseData(File xml, XMLParser.ElementContext data,
            ResourceBundle.LayoutFileBundle bundle) {
        if (data == null) {
            return;
        }

        final XMLParser.AttributeContext className = findAttribute(data, "class");
        if (className != null) {
            final String name = escapeQuotes(className.attrValue.getText(), true);
            if (StringUtils.isNotBlank(name)) {
                bundle.setBindingClass(name);
            }
        }
    }

    private XMLParser.ElementContext getDataNode(XMLParser.ElementContext root) {
        final List<XMLParser.ElementContext> data = filter(root, "data");
        if (data.size() == 0) {
            return null;
        }
        Preconditions.check(data.size() == 1, "XML layout can have only 1 data tag");
        return data.get(0);
    }

    private XMLParser.ElementContext getViewNode(File xml, XMLParser.ElementContext root) {
        final List<XMLParser.ElementContext> view = filterNot(root, "data");
        Preconditions.check(view.size() == 1, "XML layout %s must have 1 view but has %s. root"
                        + " children count %s", xml, view.size(), root.getChildCount());
        return view.get(0);
    }

    private List<XMLParser.ElementContext> filter(XMLParser.ElementContext root,
            String name) {
        List<XMLParser.ElementContext> result = new ArrayList<XMLParser.ElementContext>();
        if (root == null) {
            return result;
        }
        final XMLParser.ContentContext content = root.content();
        if (content == null) {
            return result;
        }
        for (XMLParser.ElementContext child : XmlEditor.elements(root)) {
            if (name.equals(child.elmName.getText())) {
                result.add(child);
            }
        }
        return result;
    }

    private List<XMLParser.ElementContext> filterNot(XMLParser.ElementContext root,
            String name) {
        List<XMLParser.ElementContext> result = new ArrayList<XMLParser.ElementContext>();
        if (root == null) {
            return result;
        }
        final XMLParser.ContentContext content = root.content();
        if (content == null) {
            return result;
        }
        for (XMLParser.ElementContext child : XmlEditor.elements(root)) {
            if (!name.equals(child.elmName.getText())) {
                result.add(child);
            }
        }
        return result;
    }

    private boolean hasMergeInclude(XMLParser.ElementContext rootView) {
        return "merge".equals(rootView.elmName.getText()) && filter(rootView, "include").size() > 0;
    }

    private void stripFile(File xml, File out, String encoding,
            LayoutXmlProcessor.OriginalFileLookup originalFileLookup)
            throws ParserConfigurationException, IOException, SAXException,
            XPathExpressionException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xml);
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        File actualFile = originalFileLookup == null ? null
                : originalFileLookup.getOriginalFileFor(xml);
        // TODO get rid of original file lookup
        if (actualFile == null) {
            actualFile = xml;
        }
        // always create id from actual file when available. Gradle may duplicate files.
        String noExt = ParserHelper.stripExtension(actualFile.getName());
        String binderId = actualFile.getParentFile().getName() + '/' + noExt;
        // now if file has any binding expressions, find and delete them
        boolean changed =false;//TODO;
        if (changed) {
            stripBindingTags(xml, out, binderId, encoding);
        } else if (!xml.equals(out)){
            FileUtils.copyFile(xml, out);
        }
    }


    private List<Node> get(Document doc, XPath xPath, String pattern)
            throws XPathExpressionException {
        final XPathExpression expr = xPath.compile(pattern);
        return toList((NodeList) expr.evaluate(doc, XPathConstants.NODESET));
    }

    private List<Node> toList(NodeList nodeList) {
        List<Node> result = new ArrayList<Node>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            result.add(nodeList.item(i));
        }
        return result;
    }

    private void stripBindingTags(File xml, File output, String newTag, String encoding) throws IOException {
        String res = XmlEditor.strip(xml, newTag, encoding);
        Preconditions.checkNotNull(res, "layout file should've changed %s", xml.getAbsolutePath());
        if (res != null) {
            L.d("file %s has changed, overwriting %s", xml.getName(), xml.getAbsolutePath());
            FileUtils.writeStringToFile(output, res, encoding);
        }
    }

    private static String findEncoding(File f) throws IOException {
        FileInputStream fin = new FileInputStream(f);
        try {
            UniversalDetector universalDetector = new UniversalDetector(null);

            byte[] buf = new byte[4096];
            int nread;
            while ((nread = fin.read(buf)) > 0 && !universalDetector.isDone()) {
                universalDetector.handleData(buf, 0, nread);
            }

            universalDetector.dataEnd();

            String encoding = universalDetector.getDetectedCharset();
            if (encoding == null) {
                encoding = "utf-8";
            }
            return encoding;
        } finally {
            fin.close();
        }
    }

    private static Map<String, String> attributeMap(XMLParser.ElementContext root) {
        final Map<String, String> result = new HashMap<String, String>();
        for (XMLParser.AttributeContext attr : XmlEditor.attributes(root)) {
            result.put(escapeQuotes(attr.attrName.getText(), false),
                    escapeQuotes(attr.attrValue.getText(), true));
        }
        return result;
    }

    private static XMLParser.AttributeContext findAttribute(XMLParser.ElementContext element,
            String name) {
        for (XMLParser.AttributeContext attr : element.attribute()) {
            if (escapeQuotes(attr.attrName.getText(), false).equals(name)) {
                return attr;
            }
        }
        return null;
    }

    private static String escapeQuotes(String textWithQuotes, boolean unescapeValue) {
        char first = textWithQuotes.charAt(0);
        int start = 0, end = textWithQuotes.length();
        if (first == '"' || first == '\'') {
            start = 1;
        }
        char last = textWithQuotes.charAt(textWithQuotes.length() - 1);
        if (last == '"' || last == '\'') {
            end -= 1;
        }
        String val = textWithQuotes.substring(start, end);
        if (unescapeValue) {
            return StringUtils.unescapeXml(val);
        } else {
            return val;
        }
    }
}
