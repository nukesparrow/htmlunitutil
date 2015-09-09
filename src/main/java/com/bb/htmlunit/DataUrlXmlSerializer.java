/*
 * Copyright 2015 Nuke Sparrow <nukesparrow@bitmessage.ch>.
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

// TODO : Import copyrights from HtmlUnit's DataUrlXmlSerializer

package com.bb.htmlunit;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.SgmlPage;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;

/**
 * Utility to handle conversion from HTML code to XML string.
 * @version $Revision: 9168 $
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
class DataUrlXmlSerializer {

    private static final Logger LOG = Logger.getLogger(DataUrlXmlSerializer.class.getName());

    private StringBuilder buffer_ = null;
    private StringBuilder indent_ = null;

    public String toDataUrl(final HtmlPage page) throws IOException {
        if (page.getDocumentElement() == null) {
            return null;
        }
        return "data:text/html;charset="+page.getPageEncoding()+";base64,"+Base64.encodeBase64String(asXml(page.getDocumentElement()).getBytes(page.getPageEncoding()));
    }

    private boolean failOnIOExceptions = false;

    public boolean isFailOnIOExceptions() {
        return failOnIOExceptions;
    }

    public void setFailOnIOExceptions(boolean failOnIOExceptions) {
        this.failOnIOExceptions = failOnIOExceptions;
    }

    private boolean downloadIfNecessary = false;

    public boolean isDownloadIfNecessary() {
        return downloadIfNecessary;
    }

    public void setDownloadIfNecessary(boolean downloadIfNecessary) {
        this.downloadIfNecessary = downloadIfNecessary;
    }

    /**
     * Converts an HTML element to XML.
     * @param node a node
     * @return the text representation according to the setting of this serializer
     * @throws IOException in case of problem saving resources
     */
    public String asXml(final HtmlElement node) throws IOException {
        if (node == null) {
            throw new NullPointerException();
        }
        
        StringBuilder saveBuffer = buffer_, saveIndent = indent_;
        
        buffer_ = new StringBuilder();
        indent_ = new StringBuilder();
        final SgmlPage page = node.getPage();
        if (null != page && page.isHtmlPage()) {
            final String charsetName = page.getPageEncoding();
            if (charsetName != null && node instanceof HtmlHtml) {
                buffer_.append("<?xml version=\"1.0\" encoding=\"").append(charsetName).append("\"?>").append('\n');
            }
        }
        printXml(node);
        final String response = buffer_.toString();
        buffer_ = saveBuffer;
        indent_ = saveIndent;
        return response;
    }

    private static final Set selfClosingTags = new HashSet(Arrays.asList(new String[] {
        "meta", "base", "link",
        "img", "embed",
        "colgroup",
        "frame",
        "input",
        "hr",
        "area", "br", "col", "command", "keygen", "param", "source", "track", "wbr", 
    }));

    protected void printXml(final DomElement node) throws IOException {
        if (!isExcluded(node)) {
            final boolean hasChildren = node.getFirstChild() != null;
            buffer_.append(indent_).append('<');
            printOpeningTag(node);

            // TODO : DomElement.isEmptyXmlTagExpanded has protected access
            //if (!hasChildren && !node.isEmptyXmlTagExpanded()) {
            if (!hasChildren && selfClosingTags.contains(node.getTagName())) {
                buffer_.append("/>").append('\n');
            }
            else {
                buffer_.append(">").append('\n');
                for (DomNode child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                    indent_.append("  ");
                    if (child instanceof DomElement) {
                        printXml((DomElement) child);
                    }
                    else {
                        buffer_.append(child);
                    }
                    indent_.setLength(indent_.length() - 2);
                }
                buffer_.append(indent_).append("</").append(node.getTagName()).append('>').append('\n');
            }
        }
    }

    /**
     * Prints the content between "&lt;" and "&gt;" (or "/&gt;") in the output of the tag name
     * and its attributes in XML format.
     * @param node the node whose opening tag is to be printed
     * @throws IOException in case of problem saving resources
     */
    protected void printOpeningTag(final DomElement node) throws IOException {
        buffer_.append(node.getTagName());
        final Map<String, DomAttr> attributes = readAttributes(node);

        for (final Map.Entry<String, DomAttr> entry : attributes.entrySet()) {
            buffer_.append(" ");
            buffer_.append(entry.getKey());
            buffer_.append("=\"");
            final String value = entry.getValue().getNodeValue();
            buffer_.append(com.gargoylesoftware.htmlunit.util.StringUtils.escapeXmlAttributeValue(value));
            buffer_.append('"');
        }
    }

    private Map<String, DomAttr> readAttributes(final DomElement node) throws IOException {
        if (node instanceof HtmlImage) {
            return getAttributesFor((HtmlImage) node);
        }
        else if (node instanceof HtmlLink) {
            return getAttributesFor((HtmlLink) node);
        }
        else if (node instanceof BaseFrameElement) {
            return getAttributesFor((BaseFrameElement) node);
        }

        Map<String, DomAttr> attributes = node.getAttributesMap();
        if (node instanceof HtmlOption) {
            attributes = new HashMap<String, DomAttr>(attributes);
            final HtmlOption option = (HtmlOption) node;
            if (option.isSelected()) {
                if (!attributes.containsKey("selected")) {
                    attributes.put("selected", new DomAttr(node.getPage(), null, "selected", "selected", false));
                }
            }
            else {
                attributes.remove("selected");
            }
        }
        return attributes;
    }

    private Map<String, DomAttr> getAttributesFor(final BaseFrameElement frame) throws IOException {
        final Map<String, DomAttr> map = createAttributesCopyWithClonedAttribute(frame, "src");
        final DomAttr srcAttr = map.get("src");
        if (srcAttr == null) {
            return map;
        }

        final Page enclosedPage = frame.getEnclosedPage();

        if (enclosedPage != null) {
            if (enclosedPage.isHtmlPage()) {
                srcAttr.setValue(toDataUrl((HtmlPage)enclosedPage));
            }
            else {
                srcAttr.setValue(Util.toDataUrl(enclosedPage.getWebResponse()));
            }
        }

        return map;
    }

    protected Map<String, DomAttr> getAttributesFor(final HtmlLink link) throws IOException {
        final Map<String, DomAttr> map = createAttributesCopyWithClonedAttribute(link, "href");
        final DomAttr hrefAttr = map.get("href");
        if ((null != hrefAttr) && StringUtils.isNotBlank(hrefAttr.getValue())) {
            try {
                WebResponse response = link.getWebResponse(downloadIfNecessary);
                if (response == null)
                    hrefAttr.setValue(((HtmlPage) link.getPage()).getFullyQualifiedUrl(hrefAttr.getValue()).toString());
                else
                    hrefAttr.setValue(Util.toDataUrl(response));
            } catch (IOException ex) {
                if (failOnIOExceptions)
                    throw ex;
            }
        }

        return map;
    }

    protected Map<String, DomAttr> getAttributesFor(final HtmlImage image) throws IOException {
        final Map<String, DomAttr> map = createAttributesCopyWithClonedAttribute(image, "src");
        final DomAttr srcAttr = map.get("src");
        if ((null != srcAttr) && StringUtils.isNotBlank(srcAttr.getValue())) {
            try {
                WebResponse response = image.getWebResponse(downloadIfNecessary);
                if (response == null)
                    srcAttr.setValue(((HtmlPage) image.getPage()).getFullyQualifiedUrl(srcAttr.getValue()).toString());
                else
                    srcAttr.setValue(Util.toDataUrl(response));
            } catch (IOException ex) {
                if (failOnIOExceptions)
                    throw ex;
            }
        }

        return map;
    }

    private Map<String, DomAttr> createAttributesCopyWithClonedAttribute(final HtmlElement elt, final String attrName) {
        final Map<String, DomAttr> newMap = new HashMap<String, DomAttr>(elt.getAttributesMap());

        // clone the specified element, if possible
        final DomAttr attr = newMap.get(attrName);
        if (null == attr) {
            return newMap;
        }

        final DomAttr clonedAttr = new DomAttr(attr.getPage(), attr.getNamespaceURI(),
            attr.getQualifiedName(), attr.getValue(), attr.getSpecified());

        newMap.put(attrName, clonedAttr);

        return newMap;
    }

    protected boolean isExcluded(final DomElement element) {
        return element instanceof HtmlScript;
    }

}
