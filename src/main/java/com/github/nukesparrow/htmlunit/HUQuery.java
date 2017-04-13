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

import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.TopLevelWindow;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HTMLParserListener;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlScript;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import com.gargoylesoftware.htmlunit.javascript.background.JavaScriptExecutor;
import com.gargoylesoftware.htmlunit.javascript.host.html.HTMLElement;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sourceforge.htmlunit.corejs.javascript.BaseFunction;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextAction;
import net.sourceforge.htmlunit.corejs.javascript.ContextFactory;
import net.sourceforge.htmlunit.corejs.javascript.Script;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQuery implements AutoCloseable {

    private static void disableLoudLogger(String name) {
        Logger logger = Logger.getLogger(name);
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        logger.setFilter((f) -> false);
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
    }

    public static void disableLoudLoggers() {
        disableLoudLogger("com.gargoylesoftware.htmlunit.javascript.host.dom.Document");
        disableLoudLogger("com.gargoylesoftware.htmlunit.javascript.regexp.HtmlUnitRegExpProxy");
        disableLoudLogger("com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine");
        disableLoudLogger("com.gargoylesoftware.htmlunit.javascript.StrictErrorReporter");
        disableLoudLogger("com.gargoylesoftware.htmlunit.html.HtmlScript");
        disableLoudLogger("org.apache.http.client.protocol.ResponseProcessCookies");
        disableLoudLogger("com.gargoylesoftware.htmlunit.WebConsole");
        disableLoudLogger("com.gargoylesoftware.htmlunit.html.BaseFrameElement");
    }
    
    public final WebClient webClient;
    
    public DownloaderHttpWebConnection downloaderHttpWebConnection;

    public HUQuery(WebClient webClient) {
        this.webClient = webClient;
        
        webClient.getOptions().setThrowExceptionOnScriptError(false);

        try {
            webClient.getWebConnection().close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        webClient.setWebConnection(downloaderHttpWebConnection = new DownloaderHttpWebConnection(webClient) {
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

        JavaScriptExecutor x = webClient.getJavaScriptEngine().getJavaScriptExecutor();
        if (x != null) {
            x.shutdown();
        }

        webClient.setJavaScriptEngine(new JavaScriptEngine(webClient) {
            
            @Override
            public Script compile(HtmlPage owningPage, Scriptable scope, String sourceCode, String sourceName, int startLine) {
                return super.compile(owningPage, scope, preProcessScript(sourceCode), sourceName, startLine);
            }

            @Override
            public void initialize(WebWindow webWindow) {
                super.initialize(webWindow);
                if (initializerScript != null && webWindow.getEnclosedPage() instanceof HtmlPage) {
                    execute((HtmlPage)webWindow.getEnclosedPage(), initializerScript, "HUQuery Initializer Script", 1);
                }
            }

            @Override
            public Object callFunction(HtmlPage page, net.sourceforge.htmlunit.corejs.javascript.Function function, Scriptable scope, Scriptable thisObject, Object[] args) {
                try {
                    return super.callFunction(page, function, scope, thisObject, args);
                } catch (StackOverflowError err) {
                    throw new RuntimeException(err);
                }
            }

            @Override
            public Object execute(HtmlPage page, Scriptable scope, Script script) {
                try {
                    return super.execute(page, scope, script);
                } catch (StackOverflowError err) {
                    throw new RuntimeException(err);
                }
            }

        });

    }

    public HUQuery() {
        this(new WebClient());
    }
    
    private String initializerScript = null;

    public HUQuery initializerScript(String script) {
        initializerScript = script;
        return this;
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

    public HUQuery blockAllExceptHosts(String... hosts) {
        webRequestBlockers.add((url) -> {
            String urlHost = url.getHost().toLowerCase();
            
            for (String host : hosts) {
                if (host.equals(urlHost) || urlHost.endsWith("." + host)) {
                    return false;
                }
            }
            
            return true;
        });
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
    
    private Map<String, Object> logScriptEvent(String message, HtmlPage page, Map<String, Object> scriptEvent) {
        Map<String, Object> event = dwc.createEvent();
        event.put("mark", message == null ? "JavaScript Event" : message);
        event.put("script", scriptEvent);
        dwc.logEvent(event);
        return event;
    }
            
    private void uncompressJavaScript(final String scriptSource, Map<String, Object> scriptEvent) {

        final ContextFactory factory = new ContextFactory();
        final ContextAction action = new ContextAction() {
            public Object run(final Context cx) {
                cx.setOptimizationLevel(-1);
                final Script script = cx.compileString(scriptSource, "script", 0, null);
                return cx.decompileScript(script, 4);
            }
        };

        try {
            final String decompileScript = (String) factory.call(action);

            scriptEvent.put("beautifulized", decompileScript);
        }
        catch (final Exception e) {

            scriptEvent.put("beautifulizerError", e.toString());
        }
    }
    
    private Collection<Function<String, String>> scriptPreProcessors = new ArrayList<>();

    public void addScriptPreProcessor(Function<String, String> scriptPreProcessor) {
        scriptPreProcessors.add(scriptPreProcessor);
    }
    
    private String preProcessScript(String sourceCode) {
        for (Function<String, String> pp : scriptPreProcessors) {
            final String s = sourceCode;
            sourceCode = pp.apply(sourceCode);
            if (!s.equals(sourceCode)) {
                logScriptEvent("JavaScript: pre-processed script modified", null, new LinkedHashMap() {{
                    put("sourceCode", s);
                }});
            }
        }
        return sourceCode;
    }

    private void setupDebug() {
        JavaScriptExecutor x = webClient.getJavaScriptEngine().getJavaScriptExecutor();
        if (x != null) {
            x.shutdown();
        }
        webClient.setJavaScriptEngine(new JavaScriptEngine(webClient) {
            
            @Override
            public void initialize(WebWindow webWindow) {
                super.initialize(webWindow);
                if (initializerScript != null && webWindow.getEnclosedPage() instanceof HtmlPage) {
                    execute((HtmlPage)webWindow.getEnclosedPage(), initializerScript, "HUQuery Initializer Script", 1);
                }
            }
            
            private int functionCalls = 0;
            private Map lastFunctionCallEvent = null;
            private Map lastFunctionCall = null;

            @Override
            public Object callFunction(HtmlPage page, net.sourceforge.htmlunit.corejs.javascript.Function function, Scriptable scope, Scriptable thisObject, Object[] args) {

                if (functionCalls <= 1000) {
                    String f = "";

                    if (function instanceof BaseFunction) {
                        BaseFunction bf = ((BaseFunction)function);
                        f = ": " + bf.getFunctionName();
                    }
                    
                    if (functionCalls == 1000) {
                        f += ": Too many function calls recorded, future calls will not be logged";
                    }
                    
                    if (lastFunctionCallEvent != null && lastFunctionCall.get("function").equals(function.toString())) {
                        Number seen = (Number) lastFunctionCallEvent.get("count");

                        if (seen == null) {
                            seen = 2;
                        }
                        else {
                            seen = seen.intValue() + 1;
                        }

                        lastFunctionCallEvent.put("mark", "JavaScript ("+seen+"x): callFunction" + f);
                        lastFunctionCallEvent.put("count", seen);
                    }
                    else {
                        functionCalls++;

                        lastFunctionCallEvent = logScriptEvent("JavaScript: callFunction" + f, page, lastFunctionCall = new ConcurrentHashMap() {{
                            put("function", function.toString());
                            put("scope", scope.toString());

                            if (thisObject instanceof HTMLElement) {
                                HtmlElement n = ((HTMLElement)thisObject).getDomNodeOrNull();
                                if (n != null) {
                                    if (n instanceof HtmlScript) {
                                        put("sourceCode", ((HtmlScript)n).getTextContent());
                                    }
                                    put("thisObjectHtml", n.asXml());
                                }
                            }

                            put("thisObject", thisObject.toString());
                            put("args", args.toString());
                        }});
                    }
                }

                try {
                    return super.callFunction(page, function, scope, thisObject, args);
                } catch (StackOverflowError err) {
                    throw new RuntimeException(err);
                } catch (Error err) {
                    logScriptEvent("JavaScript function caused error", page, Collections.singletonMap("error", err.toString()));
                    throw err;
                }
            }

            @Override
            public Object execute(HtmlPage page, Scriptable scope, Script script) {
                String sourceCode = script.toString();
                if (!sourceCode.trim().isEmpty()) {
                    logScriptEvent("JavaScript: execute" + (sourceCode.trim().isEmpty() ? ": Empty script" : ""), null, new LinkedHashMap() {{
                        put("scope", scope.toString());
                        put("script", sourceCode);
                        uncompressJavaScript(sourceCode, this);
                    }});
                }
                
                try {
                    return super.execute(page, scope, script);
                } catch (StackOverflowError err) {
                    throw new RuntimeException(err);
                } catch (Error err) {
                    logScriptEvent("Script caused error", page, Collections.singletonMap("error", err.toString()));
                    throw err;
                }
            }

            @Override
            public Script compile(HtmlPage owningPage, Scriptable scope, String sourceCode_, String sourceName, int startLine) {
                
                sourceCode_ = preProcessScript(sourceCode_);
                String sourceCode = sourceCode_;
                
                Map<String, Object> scriptEvent = null;
                if (!sourceCode_.trim().isEmpty()) {
                    logScriptEvent("JavaScript: compile" + (sourceCode_.trim().isEmpty() ? ": Empty script" : ""), owningPage, scriptEvent = new ConcurrentHashMap() {{
                        put("scope", scope.toString());
                        put("sourceCode", sourceCode);
                        put("sourceName", sourceName);
                        put("startLine", startLine);
                    }});
                }
                Script s = super.compile(owningPage, scope, sourceCode_, sourceName, startLine);
                if (scriptEvent != null && s != null) {
                    scriptEvent.put("compiled", s.toString());
                }
                return s;
            }

        });

        webClient.setIncorrectnessListener((message, origin) -> {
            //dwc.logExtraDataEvent("Incorrect HTML", message, "Origin", String.valueOf(origin));
        });
        webClient.setJavaScriptErrorListener(new JavaScriptErrorListener() {
            @Override
            public void scriptException(HtmlPage page, ScriptException scriptException) {
                logScriptEvent("JavaScript: scriptException", page, new LinkedHashMap() {{
                    put("scriptException", scriptException.toString());
                    put("failingLineNumber", scriptException.getFailingLineNumber());
                    put("failingColumnNumber", scriptException.getFailingColumnNumber());
                    put("failingLine", scriptException.getFailingLine());
                    put("sourceCode", scriptException.getScriptSourceCode());
                }});
            }

            @Override
            public void timeoutError(HtmlPage page, long allowedTime, long executionTime) {
                logScriptEvent("JavaScript: timeoutError", page, new LinkedHashMap() {{
                    put("allowedTime", allowedTime);
                    put("executionTime", executionTime);
                }});
            }

            @Override
            public void malformedScriptURL(HtmlPage page, String url, MalformedURLException malformedURLException) {
                logScriptEvent("JavaScript: malformedScriptURL", page, new LinkedHashMap() {{
                    put("url", url);
                    put("exception", malformedURLException.toString());
                }});
            }

            @Override
            public void loadScriptError(HtmlPage page, URL scriptUrl, Exception exception) {
                logScriptEvent("JavaScript: loadScriptError", page, new LinkedHashMap() {{
                    put("url", scriptUrl);
                    put("exception", exception.toString());
                }});
            }
        });
        webClient.setAlertHandler((page, message) -> dwc.logExtraDataEvent("Alert", message));
        webClient.setAppletConfirmHandler((a) -> {
            dwc.logExtraDataEvent("Applet", "Applet confirmation", "HTML", a.asXml());
            return true;
        });
        webClient.setConfirmHandler((page, message) -> {
            dwc.logExtraDataEvent("Confirmation", "Message: " + message);
            return true;
        });
        webClient.setCssErrorHandler(new ErrorHandler() {
            @Override
            public void warning(CSSParseException csspe) throws CSSException {
                //dwc.logExtraDataEvent("CSS", "Warning: " + csspe.getMessage());
            }

            @Override
            public void error(CSSParseException csspe) throws CSSException {
                //dwc.logExtraDataEvent("CSS", "Error: " + csspe.getMessage());
            }

            @Override
            public void fatalError(CSSParseException csspe) throws CSSException {
                dwc.logExtraDataEvent("CSS", "Fatal error: " + csspe.getMessage());
            }
        });

        webClient.setHTMLParserListener(new HTMLParserListener() {
            @Override
            public void error(String message, URL url, String html, int line, int column, String key) {
                //dwc.logExtraDataEvent("HTML", "Error: " + message, "url", ""+url, "html", html, "key", key);
            }

            @Override
            public void warning(String message, URL url, String html, int line, int column, String key) {
                //dwc.logExtraDataEvent("HTML", "Warning: " + message, "url", ""+url, "html", html, "key", key);
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
    
    public HUQuery proxy(String host, int port, boolean socks) {
        webClient.getOptions().setProxyConfig(new ProxyConfig(host, port, socks));
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
    
    public void setDownloadAcceptor(Function<DownloadInfo, File> downloadAcceptor) {
        downloaderHttpWebConnection.setDownloadAcceptor(downloadAcceptor);
    }

    public void clearDownloadAcceptor() {
        downloaderHttpWebConnection.setDownloadAcceptor(null);
    }

    public void downloadNextRequest(File f) {
        if (f == null) {
            downloaderHttpWebConnection.setDownloadAcceptor(null);
            return;
        }

        downloaderHttpWebConnection.setDownloadAcceptor((di) -> {
            if (di.response.getStatusLine().getStatusCode() != 200) {
                return null;
            }
            downloaderHttpWebConnection.setDownloadAcceptor(null);
            return f;
        });
    }

    public List<HUQueryWindow<TopLevelWindow>> windows() {
        List<TopLevelWindow> ww = webClient.getTopLevelWindows();
        return new AbstractList<HUQueryWindow<TopLevelWindow>>() {
            @Override
            public HUQueryWindow<TopLevelWindow> get(int index) {
                return new HUQueryWindow(HUQuery.this, ww.get(index));
            }

            @Override
            public int size() {
                return ww.size();
            }
        };
    }
    
}
