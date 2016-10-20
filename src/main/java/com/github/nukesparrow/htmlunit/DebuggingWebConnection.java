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

// TODO : Import copyrights from HtmlUnit's DebuggingWebConnection

package com.github.nukesparrow.htmlunit;

import com.gargoylesoftware.htmlunit.WebConnection;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gargoylesoftware.htmlunit.WebResponseData;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextAction;
import net.sourceforge.htmlunit.corejs.javascript.ContextFactory;
import net.sourceforge.htmlunit.corejs.javascript.Script;

import static com.github.nukesparrow.htmlunit.Util.*;
import com.gargoylesoftware.htmlunit.InteractivePage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;
import java.io.File;
import java.io.IOError;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONValue;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class DebuggingWebConnection implements WebConnection, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DebuggingWebConnection.class.getName());

    private static final String TEMPLATE;
    private static final String TEMPLATE_MARK = "<!--SCRIPT EVENTS PLACEHOLDER-->";
    static {
        try {
            TEMPLATE = IOUtils.toString(DebuggingWebConnection.class.getResource("DebuggingWebConnection.logviewer.html"));
        } catch (IOException ex) {
            throw new IOError(ex);
        }
    }

    private static AtomicInteger webConnectionCount = new AtomicInteger();
    private static Set<DebuggingWebConnection> updated = Collections.synchronizedSet(new HashSet());
    private static volatile Thread LOG_WRITER = null;

    private static class LogWriter extends Thread {

        public LogWriter() {
            super("Log Writer");
            //setPriority(MIN_PRIORITY);
        }

        @Override
        public void run() {
            try {
                while (webConnectionCount.intValue() > 0 || !updated.isEmpty()) {
                    if (updated.isEmpty()) {
                        try {
                            sleep(100);
                        } catch (InterruptedException ex) {
                            break;
                        }

                        continue;
                    }
                    
                    for (DebuggingWebConnection dwc : updated.toArray(new DebuggingWebConnection[0])) {
                        updated.remove(dwc);
                        try {
                            dwc.saveLogInternal();
                        } catch (RuntimeException ex) {
                            LOG.log(Level.SEVERE, "webconnection log dump error", ex);
                        }
                    }
                }
            } finally {
                LOG_WRITER = null;
            }
        }

    }

    public void setWrappedWebConnection(WebConnection webConnection) {
        if (webConnection == null)
            throw new NullPointerException();
        wrapped = webConnection;
    }

    private WebConnection wrapped;
    public final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList());

    public DebuggingWebConnection(WebConnection wrapped) {
        this.wrapped = wrapped;
        webConnectionCount.incrementAndGet();
        synchronized (DebuggingWebConnection.class) {
            if (LOG_WRITER == null) {
                LOG_WRITER = new LogWriter();
                LOG_WRITER.start();
            }
        }
    }

    public DebuggingWebConnection(WebConnection wrapped, File logFile) {
        this(wrapped);
        
        this.logFile = logFile;
    }

    public DebuggingWebConnection(WebConnection wrapped, String dirName) {
        this(wrapped);
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File reportFolder_ = new File(tmpDir, dirName);
        try {
            if (reportFolder_.exists()) {
                FileUtils.forceDelete(reportFolder_);
            }
            FileUtils.forceMkdir(reportFolder_);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        logFile = new File(reportFolder_, "log.html");
    }

    private File logFile;

    public File getLogFile() {
        return logFile;
    }

    public void setLogFile(File logFile) {
        this.logFile = logFile;
        autoSaveLog();
    }
    
    public String asJSON() {
        try {
            StringBuilder b = new StringBuilder();
            b.append('[');
            for (final Object e : events.toArray()) {
                if (b.length() > 1)
                    b.append(",\n");
                synchronized (e) {
                    b.append(JSONValue.toJSONString(e));
                }
            }
            b.append(']');
            return b.toString();
            //return JSONValue.toJSONString(events.toArray());
        } catch (RuntimeException ex) {
            synchronized (events) {
                LOG.log(Level.SEVERE, "Unable to convert to JSON: " + String.valueOf(events), ex);
            }
            return null;
        }
    }

    public void writeHtml(PrintStream out) {
        int i = TEMPLATE.indexOf(TEMPLATE_MARK);
        
        if (i == -1) {
            throw new IllegalStateException();
        }
        
        out.print(TEMPLATE.substring(0, i));
        
        for (final Object element : events.toArray()) {
            synchronized (element) {
                String content = JSONValue.toJSONString(element);
                String encoding = null;
                
                String cc = content.toLowerCase();
                if (cc.contains("</script>") || cc.contains("<!--")) {
                    encoding = "base64";
                    try {
                        content = Base64.encodeBase64String(content.getBytes("UTF-8"));
                    } catch (UnsupportedEncodingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                
                out.append("<script type=\"text/x-event-json\""+(encoding==null?"":" encoding=\"" + encoding + "\"")+">"+content+"</script>\n");
            }
        }
        
        out.print(TEMPLATE.substring(i + TEMPLATE_MARK.length()));
    }
    
    private long logAutoSaveInterval = 0;

    /**
     * Get the value of logAutoSaveInterval
     *
     * @return the value of logAutoSaveInterval
     */
    public long getLogAutoSaveInterval() {
        return logAutoSaveInterval;
    }

    /**
     * Set the value of logAutoSaveInterval
     *
     * @param logAutoSaveInterval new value of logAutoSaveInterval
     */
    public void setLogAutoSaveInterval(long logAutoSaveInterval) {
        this.logAutoSaveInterval = logAutoSaveInterval;
    }

    
    private static final Charset LOG_CHARSET = Charset.forName("UTF-8");
    private long nextSave = 0;
    protected void autoSaveLog() {
        if (logAutoSaveInterval == 0) {
            saveLog();
            return;
        }
        
        if (System.currentTimeMillis() < nextSave)
            return;
        saveLog();
        nextSave = System.currentTimeMillis() + logAutoSaveInterval;
    }

    public void setAutosave(boolean enabled) {
        nextSave = enabled ? 0 : Long.MAX_VALUE;
    }
    
    protected void saveLog() {
        updated.add(this);
    }

    protected void saveLogInternal() {
        if (logFile == null)
            return;

        try {
            File tmpFile = new File(logFile.getPath() + "~");
            File parentFile = tmpFile.getParentFile();
            if (parentFile != null) {
                parentFile.mkdirs();
            }
            try (PrintStream out = new PrintStream(tmpFile, LOG_CHARSET.name())) {
                writeHtml(out);
            }
            if (!tmpFile.renameTo(logFile)) {
                logFile.delete();
                if (!tmpFile.renameTo(logFile)) {
                    LOG.log(Level.SEVERE, "Unable to save log file (rename failed)");
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Unable to save log file", ex);
        }
    }

    private Throwable closed = null;
    
    @Override
    public void close() throws Exception {
        if (closed != null)
            throw new IllegalStateException("Closed already", closed);

        addMark("Log Closed");
        updated.add(this);
        
        closed = new Exception("Closed");
        
        if (LOG_WRITER == null) {
            saveLogInternal();
        } else {
            saveLog();
        }
        webConnectionCount.decrementAndGet();
        if (webConnectionCount.intValue() < 0)
            throw new IllegalStateException(webConnectionCount.toString());
        
        wrapped.close();
    }

    protected static Map boxThrowable(Throwable ex) {
        Map m = new LinkedHashMap();
        m.put("time", System.currentTimeMillis());
        m.put("message", ex.getMessage());
        m.put("class", ex.getClass().getCanonicalName());
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw, true));
        m.put("stack", sw.toString());
        return m;
    }

    protected WebClient webClient = null;

    public WebClient getWebClient() {
        return webClient;
    }

    public void setWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Calls the wrapped webconnection and save the received response.
     * {@inheritDoc}
     */
    @Override
    public WebResponse getResponse(WebRequest request) throws IOException {
        WebResponse response;
        
        Map event = mapBuilderBaseData().map;
        synchronized (event) {
            events.add(event);

            Map eventRequest = mapBuilderBaseData().map;
            event.put("request", eventRequest);

            eventRequest.put("method", String.valueOf(request.getHttpMethod()));
            eventRequest.put("url", request.getUrl().toString());
            eventRequest.put("encoding", request.getEncodingType().getName());
            eventRequest.put("addtionalHeaders", request.getAdditionalHeaders());
            if (!request.getRequestParameters().isEmpty()) {
                List rp = new ArrayList();
                eventRequest.put("requestParameters", rp);
                for (NameValuePair nameValuePair : request.getRequestParameters()) {
                    rp.add(mapBuilder().put("key", nameValuePair.getName()).put("value", nameValuePair.getValue()).map);
                }
            } else if (request.getRequestBody() != null) {
                eventRequest.put("body", request.getRequestBody());
            }
            autoSaveLog();
            
            if (webClient != null && webClient.getCookieManager().isCookiesEnabled()) {
                Set<Cookie> cookies = webClient.getCookies(request.getUrl());
                List<Map> cookiesMaps = new ArrayList(cookies.size());
                for (Cookie cookie : cookies) {
                    Map cookieMap = new LinkedHashMap();
                    cookieMap.put("name", cookie.getName());
                    cookieMap.put("value", cookie.getValue());
                    cookieMap.put("domain", cookie.getDomain());
                    cookieMap.put("path", cookie.getPath());
                    cookieMap.put("expires", String.valueOf(cookie.getExpires()));
                    cookieMap.put("httponly", cookie.isHttpOnly());
                    cookieMap.put("secure", cookie.isSecure());
                    cookiesMaps.add(cookieMap);
                }
                eventRequest.put("cookies", cookiesMaps);
            }
        }

        try {
            response = wrapped.getResponse(request);
        } catch (IOException | RuntimeException ex) {
            synchronized (event) {
                event.put("error", boxThrowable(ex));
            }
            autoSaveLog();
            
            throw ex;
        }

        final Map eventResponse = mapBuilderBaseData().map;

        synchronized (event) {
            event.put("response", eventResponse);

            eventResponse.put("statusCode", response.getStatusCode());
            eventResponse.put("statusMessage", response.getStatusMessage());
            List l = new ArrayList();
            eventResponse.put("headers", l);
            for (NameValuePair h : response.getResponseHeaders()) {
                l.add(mapBuilder().put("key", h.getName()).put("value", h.getValue()).map);
            }
            eventResponse.put("loadtime", response.getLoadTime());
            eventResponse.put("contentType", response.getContentType());
            eventResponse.put("contentCharset", response.getContentCharsetOrNull());
            eventResponse.put("contentUrl", toDataUrl(response));

            if (uncompressJavaScript && isJavaScript(response.getContentType())) {
                response = uncompressJavaScript(response);
                eventResponse.put("contentUncompressed", response.getContentAsString());
            }

            autoSaveLog();
        }
        return response;
    }

    private String lastPageDataUrl = "";

    private static class Text {
        public final String caption, text;

        public Text(String caption, String text) {
            this.caption = caption;
            this.text = text;
        }
    }
    
    public static Object text(String caption, String text) {
        return new Text(caption, text);
    }
    
    public static Object text(String text) {
        return new Text(null, text);
    }
    
    /**
     * Adds a mark that will be visible in the HTML result page generated by this class.
     * @param mark the text
     */
    public void addMark(String mark, Object... data) {
        addMark(mark, (HtmlPage)null, (Throwable)null, data);
    }

    public void addMark(String mark, InteractivePage page, Object... data) {
        addMark(mark, page, (Throwable)null, data);
    }

    public void addMark(String mark, Throwable error, Object... data) {
        addMark(mark, (HtmlPage)null, error, data);
    }

    public void addMark(String mark, InteractivePage page, Throwable error, Object... data) {
        MapBuilder mb = mapBuilderBaseData();
        List<Map> extraData = null;
        MapBuilder db = null;
        
        for (Object p : data) {
            if (p == null)
                continue;
            
            if (p instanceof Throwable) {
                if (error != null) {
                    throw new IllegalStateException();
                }
                error = (Exception) p;
                
                continue;
            }
            
            if (p instanceof HtmlPage) {
                if (page != null) {
                    throw new IllegalStateException();
                }
                page = (HtmlPage) p;
                
                continue;
            }
            
            if (p instanceof Text) {
                if (extraData == null) {
                    extraData = new ArrayList<>();
                    mb.put("extraData", extraData);
                }
                Text t = (Text) p;
                extraData.add(mapBuilder().putNotNull("caption", t.caption).put("text", t.text).map);
                
                continue;
            }
            
            String s = String.valueOf(p);
            
            if (extraData == null) {
                extraData = new ArrayList<>();
                mb.put("extraData", extraData);
            }
            
            if (db == null) {
                db = new MapBuilder();
                db.put("text", s);
                extraData.add(db.map);
            } else {
                db.put("caption", s);
                db = null;
            }
        }
        
        if (mark == null && error != null)
            mark = "Exception";
        
        Map m = mb.map;

        m.put("mark", mark);

        if (error != null)
            m.put("error", boxThrowable(error));
        if (page != null && page instanceof HtmlPage) {
            try {
                String pageDataUrl = null;
                try {
                    pageDataUrl = new DataUrlXmlSerializer().toDataUrl((HtmlPage) page);
                } catch (RuntimeException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                    pageDataUrl = "data:text/xml;charset="+page.getPageEncoding()+";base64," + Base64.encodeBase64String(page.asXml().getBytes(page.getPageEncoding()));
                }
                if (pageDataUrl != null && !lastPageDataUrl.equals(pageDataUrl)) {
                    m.put("pageUrl", pageDataUrl);
                    lastPageDataUrl = pageDataUrl;
                }
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
                try {
                    m.put("pageDataUrl", toDataUrl(page.getWebResponse()));
                } catch (IOException ex1) {
                    LOG.log(Level.SEVERE, null, ex1);
                    m.put("error", "Page load failed");
                }
            }
        }
        events.add(m);
        autoSaveLog();
    }

    /**
     * Tries to uncompress the JavaScript code in the provided response.
     * @param response the response to uncompress
     * @return a new response with uncompressed JavaScript code or the original response in case of failure
     */
    protected WebResponse uncompressJavaScript(final WebResponse response) {
        final WebRequest request = response.getWebRequest();
        final String scriptName = request.getUrl().toString();
        final String scriptSource = response.getContentAsString();

        // skip if it is already formatted? => TODO

        final ContextFactory factory = new ContextFactory();
        final ContextAction action = new ContextAction() {
            public Object run(final Context cx) {
                cx.setOptimizationLevel(-1);
                final Script script = cx.compileString(scriptSource, scriptName, 0, null);
                return cx.decompileScript(script, 4);
            }
        };

        try {
            final String decompileScript = (String) factory.call(action);
            final List<NameValuePair> responseHeaders = new ArrayList<NameValuePair>(response.getResponseHeaders());
            for (int i = responseHeaders.size() - 1; i >= 0; i--) {
                if ("content-encoding".equalsIgnoreCase(responseHeaders.get(i).getName())) {
                    responseHeaders.remove(i);
                }
            }
            final WebResponseData wrd = new WebResponseData(decompileScript.getBytes(), response.getStatusCode(),
                response.getStatusMessage(), responseHeaders);
            return new WebResponse(wrd, response.getWebRequest().getUrl(),
                response.getWebRequest().getHttpMethod(), response.getLoadTime());
        }
        catch (final Exception e) {
            LOG.log(Level.WARNING, "Failed to decompress JavaScript response. Delivering as it.", e);
        }

        return response;
    }

    static String chooseExtension(final String contentType) {
        if (isJavaScript(contentType)) {
            return ".js";
        }
        else if ("text/html".equals(contentType)) {
            return ".html";
        }
        else if ("text/css".equals(contentType)) {
            return ".css";
        }
        else if ("text/xml".equals(contentType)) {
            return ".xml";
        }
        else if ("image/gif".equals(contentType)) {
            return ".gif";
        }
        return ".txt";
    }

    /**
     * Indicates if the response contains JavaScript content.
     * @param contentType the response's content type
     * @return <code>false</code> if it is not recognized as JavaScript
     */
    static boolean isJavaScript(final String contentType) {
        return contentType.contains("javascript") || contentType.contains("ecmascript")
            || (contentType.startsWith("text/") && contentType.endsWith("js"));
    }

    private boolean uncompressJavaScript = true;

    /**
     * Indicates if it should try to format responses recognized as JavaScript.
     * @return default is <code>false</code> to deliver the original content
     */
    public boolean isUncompressJavaScript() {
        return uncompressJavaScript;
    }

    /**
     * Indicates that responses recognized as JavaScript should be formatted or not.
     * Formatting is interesting for debugging when the original script is compressed on a single line.
     * It allows to better follow with a debugger and to obtain more interesting error messages.
     * @param decompress <code>true</code> if JavaScript responses should be uncompressed
     */
    public void setUncompressJavaScript(boolean decompress) {
        this.uncompressJavaScript = decompress;
    }

}
