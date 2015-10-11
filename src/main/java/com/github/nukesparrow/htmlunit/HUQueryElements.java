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
package com.github.nukesparrow.htmlunit;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.impl.SelectableTextInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQueryElements<Elem extends HtmlElement> implements Iterable<HUQueryElements<Elem>> {

    public final HUQueryWindow w;
    public final List<Elem> elements;
    
    HUQueryElements(HUQueryWindow w, List<Elem> elements) {
        this.w = w;
        this.elements = elements;
    }
    
    HUQueryElements(HUQueryWindow w, Elem element) {
        this.w = w;
        this.elements = Collections.singletonList(element);
    }
    
    public HUQueryElements<? extends HtmlElement> e(String selector) {
        return new HUQueryElements(w, e().querySelectorAll(selector));
    }

    public Elem e() {
        return elements.get(0);
    }

    public void click() {
        try {
            e().click();
        } catch (IOException ex) {
            throw new HUQueryException(ex);
        }
    }

    public void type(String text) {
        try {
            HtmlElement e = e();
            e.click();
            if (e instanceof SelectableTextInput) {
                SelectableTextInput s = (SelectableTextInput) e;
                s.select();
            }
            e.type(text);
        } catch (IOException ex) {
            throw new HUQueryException(ex);
        }
    }

    public void ocr(String selector) {
        type(w.e(selector).ocr());
    }

    public String ocr() {
        HUOCR ocr = w.getOcr();
        if (ocr == null)
            throw new NullPointerException();
        byte[] img;
        try (InputStream in = ((HtmlImage)e()).getWebResponse(true).getContentAsStream()) {
            img = IOUtils.toByteArray(in);
        } catch (IOException ex) {
            throw new HUQueryException(ex);
        }
        try {
            return ocr.recognize(img).toString();
        } catch (HUOCRException ex) {
            throw new HUQueryException(ex);
        }
    }

    public String text() {
        return e().asText().trim();
    }

    public String[] text(String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text());
        if (m.find()) {
            String[] r = new String[m.groupCount() + 1];
            for(int i = 0; i <= r.length; i++) {
                r[i] = m.group(i);
            }
            return r;
            //return m.groupCount() >= 1 ? m.group(1) : m.group();
        } else {
            throw new HUQueryException("Pattern " + pattern + " not found in " + text());
        }
    }
    
    public String html() {
        return e().asXml();
    }
    
    public String attr(String name) {
        return e().getAttribute(name);
    }

    public void attr(String name, String value) {
        e().setAttribute(name, value);
    }

    public boolean found() {
        return !elements.isEmpty();
    }
    
    public void required() {
        if (!found())
            throw new HUQueryException("Required element missing");
    }

    @Override
    public Iterator<HUQueryElements<Elem>> iterator() {
        return new Iterator<HUQueryElements<Elem>>() {

            int index = -1;

            @Override
            public boolean hasNext() {
                return index + 1 < elements.size();
            }

            @Override
            public HUQueryElements<Elem> next() {
                index++;
                return new HUQueryElements(w, elements.get(index));
            }
        };
    }

}
