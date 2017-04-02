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

import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.TopLevelWindow;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebWindow;
import java.io.File;
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
    
    public HUQuery debug(File file) {
        webClient.setWebConnection(dwc = new DebuggingWebConnection(webClient.getWebConnection(), file));
        return this;
    }
    
    public HUQuery noScript() {
        webClient.getOptions().setJavaScriptEnabled(false);
        return this;
    }
    
    public HUQuery noCss() {
        webClient.getOptions().setCssEnabled(false);
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
            HUQueryWindow w = open(new URL(url));
            waitJavascript();
            return w;
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(url, ex);
        }
    }

    public HUQueryWindow<TopLevelWindow> open(URL url) {
        HUQueryWindow w = new HUQueryWindow(this, webClient.openWindow(url, ""));
        waitJavascript();
        return w;
    }

    @Override
    public void close() {
        mark("closing");
        
        webClient.close();

        if (dwc != null) {
            //dwc.close();
            dwc = null;
        }
    }

    public HUQueryWindow<TopLevelWindow> openOrFind(URL url) {
        for (TopLevelWindow w : webClient.getTopLevelWindows()) {
            if (w != null && w.getEnclosedPage() != null && url.equals(w.getEnclosedPage().getUrl())) {
                return new HUQueryWindow(this, w);
            }
        }
        
        return open(url);
    }
    
    public HUQueryWindow<WebWindow> currentWindow() {
        WebWindow w = webClient.getCurrentWindow();
        if (w == null)
            return null;
        return new HUQueryWindow(this, w);
    }

    public void waitJavascript() {
        webClient.waitForBackgroundJavaScript(webClient.getOptions().getTimeout());
    }

    public boolean filterLatin = true;

    public void silent() {
        webClient.setIncorrectnessListener((m, o) -> {});
        webClient.setJavaScriptErrorListener(new SilentJavaScriptErrorListener());
        webClient.setAlertHandler((p, m) -> {});
        webClient.setAppletConfirmHandler((a) -> true);
        webClient.setConfirmHandler((p, m) -> true);
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        webClient.setHTMLParserListener(null);
    }
    
}
