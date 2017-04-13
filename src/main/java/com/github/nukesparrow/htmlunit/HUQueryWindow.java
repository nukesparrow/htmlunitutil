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

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TopLevelWindow;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlArea;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQueryWindow<Window extends WebWindow> implements AutoCloseable {
    
    public final HUQuery q;
    public final Window w;

    HUQueryWindow(HUQuery q, Window window) {
        this.q = q;
        this.w = window;
    }
    
    HUOCR ocr = null;

    public HUOCR getOcr() {
        return ocr == null ? q.getOcr() : ocr;
    }

    public void setOcr(HUOCR ocr) {
        this.ocr = ocr;
    }
    
    public HUQueryElements<? extends HtmlElement> e;

    public HUQueryElements<? extends HtmlElement> e(String selector) {
        return e = new HUQueryElements(this, Util.recursiveSelect((HtmlPage)w.getEnclosedPage(), selector, new ArrayList()));
    }

    public HUQueryElements<? extends HtmlElement> e(HtmlElement e) {
        return this.e = new HUQueryElements(this, e);
    }
    
    public void mark(String mark) {
        if (q.dwc == null)
            return;
        if (w.getEnclosedPage() instanceof HtmlPage) {
            q.dwc.addMark(mark, ((HtmlPage)w.getEnclosedPage()));
        } else {
            q.dwc.addMark(mark);
        }
    }

    public void fail(Throwable error) {
        fail(error, "Error");
    }

    public void fail(Throwable error, String mark) {
        if (q.dwc == null)
            return;
        if (w.getEnclosedPage() instanceof HtmlPage) {
            q.dwc.addMark(mark, error, ((HtmlPage)w.getEnclosedPage()));
        } else {
            q.dwc.addMark(mark, error);
        }
    }

    @Override
    public void close() {
        if (w instanceof TopLevelWindow)
            ((TopLevelWindow)w).close();
        else
            throw new HUQueryException("Unable to close frame window");
    }

    public boolean isUrl(String test) {
        String url = w.getEnclosedPage().getUrl().toString();
        if (test.startsWith("/")) {
            return url.endsWith(test);
        } else {
            return url.equals(test);
        }
    }
    
    public Page p() {
        return w.getEnclosedPage();
    }

    public HtmlPage htmlPage() {
        return p().isHtmlPage() ? (HtmlPage) p() : null;
    }
    
    public HUQueryElements<?> clickable = null;

    public HUQueryElements<?> getClickable() {
        return clickable = new HUQueryElements(this, getClickableElements());
    }

    public List<HtmlElement> getClickableElements() {
        return getClickableElements(htmlPage().getBody());
    }

    public HUQueryElements<?> getClickable(HtmlElement root) {
        return clickable = new HUQueryElements(this, getClickableElements());
    }

    public List<HtmlElement> getClickableElements(HtmlElement root) {
        List<HtmlElement> clickable = new ArrayList<>();

        // TODO : check for all clickable element types
        for (HtmlElement e : root.getHtmlElementDescendants()) {
            if ((e instanceof HtmlAnchor) && e.hasAttribute("href")) {
                clickable.add(e);
            }
            else if ((e instanceof HtmlArea) && e.hasAttribute("href")) {
                clickable.add(e);
            }
            else if (e.hasAttribute("onclick") || e.hasEventHandlers("click")) {
                clickable.add(e);
            }
        }
        
        return clickable;
    }

    public HUQueryElements none() {
        return new HUQueryElements(this);
    }

}
