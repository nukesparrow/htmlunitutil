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
import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.TopLevelWindow;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindow;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.logging.Level;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQuery implements AutoCloseable {

    public final WebClient webClient;

    public HUQuery(WebClient webClient) {
        this.webClient = webClient;

        try {
            webClient.getWebConnection().close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        webClient.setWebConnection(new DownloaderHttpWebConnection(webClient) {
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                
                if (shouldBlockWebRequest(request.getUrl())) {
                    return new StringWebResponse("", request.getUrl());
                }
                
                return super.getResponse(request);
            }

        });
    }

    public HUQuery() {
        this.webClient = new WebClient();
    }
    
    protected HashSet<Function<URL, Boolean>> webRequestBlockers = new LinkedHashSet<Function<URL, Boolean>>();

    protected boolean shouldBlockWebRequest(URL url) {
        for (Function<URL, Boolean> blocker : webRequestBlockers) {
            if (blocker.apply(url)) {
                return true;
            }
        }
        return false;
    }
    
    public static Function<URL, Boolean> domainBlocker(String hostname) {
        
        String host = hostname.toLowerCase();
        
        return new Function<URL, Boolean>() {
            @Override
            public Boolean apply(URL t) {
                String urlHost = t.getHost().toLowerCase();
                
                if (urlHost.length() > host.length()) {
                    return urlHost.endsWith("." + host);
                }
                
                return host.equals(urlHost);
            }

            @Override
            public String toString() {
                return "!" + host;
            }

        };
    }
    
    public HUQuery blockHosts(String... hosts) {
        for (String host : hosts) {
            blockHost(host);
        }
        return this;
    }

    public HUQuery blockHost(String host) {
        blockUrl(domainBlocker(host));
        return this;
    }

    public HUQuery blockUrl(Function<URL, Boolean> blocker) {
        webRequestBlockers.add(blocker);
        return this;
    }

    public HUQuery blockUrls(Function<URL, Boolean>... blockers) {
        webRequestBlockers.addAll(Arrays.asList(blockers));
        return this;
    }

    public HUQuery blockUrls(Collection<Function<URL, Boolean>> blockers) {
        webRequestBlockers.addAll(blockers);
        return this;
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
    
    public HUQuery blockPopularJunk() {
        blockHost("google-analytics.com");
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
    
    public HUQueryWindow w = null;

    public HUQueryWindow<TopLevelWindow> open(String url) {
        try {
            w = open(new URL(url));
            waitJavascript();
            return w;
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(url, ex);
        }
    }

    public HUQueryWindow<TopLevelWindow> open(URL url) {
        w = new HUQueryWindow(this, webClient.openWindow(url, ""));
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
        for (TopLevelWindow win : webClient.getTopLevelWindows()) {
            if (win != null && win.getEnclosedPage() != null && url.equals(win.getEnclosedPage().getUrl())) {
                return w = new HUQueryWindow(this, win);
            }
        }
        
        return open(url);
    }
    
    public HUQueryWindow<WebWindow> currentWindow() {
        WebWindow win = webClient.getCurrentWindow();
        if (win == null)
            return null;
        return w = new HUQueryWindow(this, win);
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
