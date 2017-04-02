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

import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.TopLevelWindow;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.background.JavaScriptExecutor;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import net.sourceforge.htmlunit.corejs.javascript.Decompiler;
import net.sourceforge.htmlunit.corejs.javascript.Script;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQuery implements AutoCloseable {

    public final WebClient webClient;

    public HUQuery(WebClient webClient) {
        this.webClient = webClient;
        
        webClient.getOptions().setThrowExceptionOnScriptError(false);

        try {
            webClient.getWebConnection().close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        webClient.setWebConnection(new DownloaderHttpWebConnection(webClient) {
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                
                if (shouldBlockWebRequest(request.getUrl())) {
                    return new StringWebResponse("", request.getUrl()) {

                        @Override
                        public String getStatusMessage() {
                            return "Blocked";
                        }

                    };
                }
                
                return super.getResponse(request);
            }

        });
    }

    public HUQuery() {
        this(new WebClient());
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
    
    private void setupDebug() {
        JavaScriptExecutor x = webClient.getJavaScriptEngine().getJavaScriptExecutor();
        if (x != null) {
            x.shutdown();
        }
        webClient.setJavaScriptEngine(new JavaScriptEngine(webClient) {
            
            private void logScriptEvent(String message, HtmlPage page, Map<String, Object> scriptEvent) {
                Map<String, Object> event = dwc.createEvent();
                event.put("mark", message == null ? "JavaScript Event" : message);
                event.put("script", scriptEvent);
                dwc.logEvent(event);
            }
            
            @Override
            public Object callFunction(HtmlPage page, net.sourceforge.htmlunit.corejs.javascript.Function function, Scriptable scope, Scriptable thisObject, Object[] args) {
                logScriptEvent("JavaScript: callFunction", page, new LinkedHashMap() {{
                    put("function", function.toString());
                    put("scope", scope.toString());
                    put("thisObject", thisObject.toString());
                    put("args", args.toString());
                }});
                return super.callFunction(page, function, scope, thisObject, args);
            }

            @Override
            public Object callFunction(HtmlPage page, net.sourceforge.htmlunit.corejs.javascript.Function javaScriptFunction, Scriptable thisObject, Object[] args, DomNode node) {
                logScriptEvent("JavaScript: callFunction", page, new LinkedHashMap() {{
                    put("function", javaScriptFunction.toString());
                    put("thisObject", thisObject.toString());
                    put("args", args.toString());
                    put("node", node.toString());
                }});
                return super.callFunction(page, javaScriptFunction, thisObject, args, node);
            }

            @Override
            public Object execute(HtmlPage page, Scriptable scope, Script script) {
                String sourceCode = script.toString();
                if (!sourceCode.trim().isEmpty()) {
                    logScriptEvent("JavaScript: execute" + (sourceCode.trim().isEmpty() ? ": Empty script" : ""), null, new LinkedHashMap() {{
                        put("scope", scope.toString());
                        put("script", sourceCode);
                    }});
                }
                return super.execute(page, scope, script);
            }

            @Override
            public Object execute(HtmlPage page, Script script) {
                String sourceCode = script.toString();
                if (!sourceCode.trim().isEmpty()) {
                    logScriptEvent("JavaScript: execute" + (sourceCode.trim().isEmpty() ? ": Empty script" : ""), page, new LinkedHashMap() {{
                        put("script", sourceCode);
                    }});
                }
                return super.execute(page, script);
            }

            @Override
            public Object execute(HtmlPage page, String sourceCode, String sourceName, int startLine) {
                if (!sourceCode.trim().isEmpty()) {
                    logScriptEvent("JavaScript: execute" + (sourceCode.trim().isEmpty() ? ": Empty script" : ""), page, new LinkedHashMap() {{
                        put("sourceCode", sourceCode);
                        put("sourceName", sourceName);
                        put("startLine", startLine);
                    }});
                }
                return super.execute(page, sourceCode, sourceName, startLine);
            }

            @Override
            protected void handleJavaScriptException(ScriptException scriptException, boolean triggerOnError) {
                logScriptEvent("JavaScript: handleJavaScriptException", null, new LinkedHashMap() {{
                    put("scriptException", scriptException.toString());
                    put("triggerOnError", triggerOnError);
                }});
                super.handleJavaScriptException(scriptException, triggerOnError);
            }

            @Override
            public Script compile(HtmlPage owningPage, Scriptable scope, String sourceCode, String sourceName, int startLine) {
                Map<String, Object> scriptEvent = null;
                if (!sourceCode.trim().isEmpty()) {
                    logScriptEvent("JavaScript: compile" + (sourceCode.trim().isEmpty() ? ": Empty script" : ""), owningPage, scriptEvent = new ConcurrentHashMap() {{
                        put("scope", scope.toString());
                        put("sourceCode", sourceCode);
                        put("sourceName", sourceName);
                        put("startLine", startLine);
                    }});
                }
                Script s = super.compile(owningPage, scope, sourceCode, sourceName, startLine);
                if (scriptEvent != null && s != null) {
                    scriptEvent.put("compiled", s.toString());
                }
                return s;
            }

            @Override
            public Script compile(HtmlPage page, String sourceCode, String sourceName, int startLine) {
                Map<String, Object> scriptEvent = null;
                if (!sourceCode.trim().isEmpty()) {
                    logScriptEvent("JavaScript: compile" + (sourceCode.trim().isEmpty() ? ": Empty script" : ""), page, scriptEvent = new ConcurrentHashMap() {{
                        put("sourceCode", sourceCode);
                        put("sourceName", sourceName);
                        put("startLine", startLine);
                    }});
                }
                Script s = super.compile(page, sourceCode, sourceName, startLine);
                if (scriptEvent != null && s != null) {
                    scriptEvent.put("compiled", s.toString());
                }
                return s;
            }

        });
    }
    
    public HUQuery debug() {
        webClient.setWebConnection(dwc = new DebuggingWebConnection(webClient.getWebConnection()));
        setupDebug();
        return this;
    }
    
    public HUQuery debug(String dir) {
        webClient.setWebConnection(dwc = new DebuggingWebConnection(webClient.getWebConnection(), dir));
        setupDebug();
        return this;
    }
    
    public HUQuery debug(File file) {
        webClient.setWebConnection(dwc = new DebuggingWebConnection(webClient.getWebConnection(), file));
        setupDebug();
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
        waitJavascript(webClient.getOptions().getTimeout());
    }

    public void waitJavascript(long duration) {
        webClient.waitForBackgroundJavaScriptStartingBefore(duration);
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
