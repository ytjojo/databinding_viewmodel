/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.databinding.parser.XMLParser;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Ugly inefficient class to strip unwanted tags from XML.
 * Band-aid solution to unblock development
 */
public class XmlEditor {


    private static <T extends XMLParser.ElementContext> List<T>
            filterNodesByName(String name, Iterable<T> items) {
        List<T> result = new ArrayList<T>();
        for (T item : items) {
            if (name.equals(nodeName(item))) {
                result.add(item);
            }
        }
        return result;
    }

    private static <T extends XMLParser.ElementContext> List<T>
            excludeNodesByName(String name, Iterable<T> items) {
        List<T> result = new ArrayList<T>();
        for (T item : items) {
            if (!name.equals(nodeName(item))) {
                result.add(item);
            }
        }
        return result;
    }

    private static Position toPosition(Token token) {
        return new Position(token.getLine() - 1, token.getCharPositionInLine());
    }

    private static Position toEndPosition(Token token) {
        return new Position(token.getLine() - 1,
                token.getCharPositionInLine() + token.getText().length());
    }

    public static String nodeName(XMLParser.ElementContext elementContext) {
        return elementContext.elmName.getText();
    }

    public static List<? extends XMLParser.AttributeContext> attributes(XMLParser.ElementContext elementContext) {
        if (elementContext.attribute() == null)
            return new ArrayList<XMLParser.AttributeContext>();
        else {
            return elementContext.attribute();
        }
    }

    public static List<? extends XMLParser.AttributeContext> expressionAttributes(
            XMLParser.ElementContext elementContext) {
        List<XMLParser.AttributeContext> result = new ArrayList<XMLParser.AttributeContext>();
        for (XMLParser.AttributeContext input : attributes(elementContext)) {
            String attrName = input.attrName.getText();
            boolean isExpression = attrName.equals("android:tag");
            if (!isExpression) {
                final String value = input.attrValue.getText();
                isExpression = isExpressionText(input.attrValue.getText());
            }
            if (isExpression) {
                result.add(input);
            }
        }
        return result;
    }

    private static boolean isExpressionText(String value) {
        // Check if the expression ends with "}" and starts with "@{" or "@={", ignoring
        // the surrounding quotes.
        return (value.length() > 5 && value.charAt(value.length() - 2) == '}' &&
                ("@{".equals(value.substring(1, 3)) || "@={".equals(value.substring(1, 4))));
    }

    private static Position endTagPosition(XMLParser.ElementContext context) {
        if (context.content() == null) {
            // no content, so just choose the start of the "/>"
            Position endTag = toPosition(context.getStop());
            if (endTag.charIndex <= 0) {
                L.e("invalid input in %s", context);
            }
            return endTag;
        } else {
            // tag with no attributes, but with content
            Position position = toPosition(context.content().getStart());
            if (position.charIndex <= 0) {
                L.e("invalid input in %s", context);
            }
            position.charIndex--;
            return position;
        }
    }

    public static List<? extends XMLParser.ElementContext> elements(XMLParser.ElementContext context) {
        if (context.content() != null && context.content().element() != null) {
            return context.content().element();
        }
        return new ArrayList<XMLParser.ElementContext>();
    }

    private static boolean replace(ArrayList<String> lines, Position start, Position end,
            String text) {
        fixPosition(lines, start);
        fixPosition(lines, end);
        if (start.line != end.line) {
            String startLine = lines.get(start.line);
            String newStartLine = startLine.substring(0, start.charIndex) + text;
            lines.set(start.line, newStartLine);
            for (int i = start.line + 1; i < end.line; i++) {
                String line = lines.get(i);
                lines.set(i, replaceWithSpaces(line, 0, line.length() - 1));
            }
            String endLine = lines.get(end.line);
            String newEndLine = replaceWithSpaces(endLine, 0, end.charIndex - 1);
            lines.set(end.line, newEndLine);
            return true;
        } else if (end.charIndex - start.charIndex >= text.length()) {
            String line = lines.get(start.line);
            int endTextIndex = start.charIndex + text.length();
            String replacedText = replaceRange(line, start.charIndex, endTextIndex, text);
            String spacedText = replaceWithSpaces(replacedText, endTextIndex, end.charIndex - 1);
            lines.set(start.line, spacedText);
            return true;
        } else {
            String line = lines.get(start.line);
            String newLine = replaceWithSpaces(line, start.charIndex, end.charIndex - 1);
            lines.set(start.line, newLine);
            return false;
        }
    }

    private static String replaceRange(String line, int start, int end, String newText) {
        return line.substring(0, start) + newText + line.substring(end);
    }

    public static boolean hasExpressionAttributes(XMLParser.ElementContext context) {
        List<? extends XMLParser.AttributeContext> expressions = expressionAttributes(context);
        int size = expressions.size();
        if (size == 0) {
            return false;
        } else if (size > 1) {
            return true;
        } else {
            // android:tag is included, regardless, so we must only count as an expression
            // if android:tag has a binding expression.
            return isExpressionText(expressions.get(0).attrValue.getText());
        }
    }


    private static PositionPair findTerminalPositions(XMLParser.ElementContext node,
            ArrayList<String> lines) {
        Position endPosition = toEndPosition(node.getStop());
        Position startPosition = toPosition(node.getStop());
        int index;
        do {
            index = lines.get(startPosition.line).lastIndexOf("</");
            startPosition.line--;
        } while (index < 0);
        startPosition.line++;
        startPosition.charIndex = index;
        //noinspection unchecked
        return new PositionPair(startPosition, endPosition);
    }

    private static String replaceWithSpaces(String line, int start, int end) {
        StringBuilder lineBuilder = new StringBuilder(line);
        for (int i = start; i <= end; i++) {
            lineBuilder.setCharAt(i, ' ');
        }
        return lineBuilder.toString();
    }

    private static void fixPosition(ArrayList<String> lines, Position pos) {
        String line = lines.get(pos.line);
        while (pos.charIndex > line.length()) {
            pos.charIndex--;
        }
    }

    private static class Position {

        int line;
        int charIndex;

        public Position(int line, int charIndex) {
            this.line = line;
            this.charIndex = charIndex;
        }
    }

    private static class TagAndContext {
        private final String mTag;
        private final XMLParser.ElementContext mElementContext;

        private TagAndContext(String tag, XMLParser.ElementContext elementContext) {
            mTag = tag;
            mElementContext = elementContext;
        }

        private XMLParser.ElementContext getContext() {
            return mElementContext;
        }

        private String getTag() {
            return mTag;
        }
    }

    private static class PositionPair {
        private final Position left;
        private final Position right;

        private PositionPair(Position left, Position right) {
            this.left = left;
            this.right = right;
        }
    }
}
