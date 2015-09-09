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
package com.bb.htmlunit;

import com.gargoylesoftware.htmlunit.TopLevelWindow;
import com.gargoylesoftware.htmlunit.WebClient;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQuery implements AutoCloseable {

    public final WebClient webClient;

    public HUQuery(WebClient webClient) {
        this.webClient = webClient;
    }

    public HUQuery() {
        this.webClient = new WebClient();
    }
    
    DebuggingWebConnection dwc = null;
    
    public HUQuery debug() {
        webClient.setWebConnection(dwc = new DebuggingWebConnection(webClient.getWebConnection()));
        return this;
    }
    
    public HUQuery debug(String dir) {
        webClient.setWebConnection(dwc = new DebuggingWebConnection(webClient.getWebConnection(), dir));
        return this;
    }
    
    public void mark(String mark) {
        if (dwc == null)
            return;
        dwc.addMark(mark);
    }

    HUOCR ocr;

    public HUOCR getOcr() {
        return ocr;
    }

    public void setOcr(HUOCR ocr) {
        this.ocr = ocr;
    }

    public HUQueryWindow<TopLevelWindow> open(String url) {
        try {
            return open(new URL(url));
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(url, ex);
        }
    }

    public HUQueryWindow<TopLevelWindow> open(URL url) {
        return new HUQueryWindow(this, webClient.openWindow(url, ""));
    }

    @Override
    public void close() {
        mark("closing");
        webClient.closeAllWindows();
    }
    
}
