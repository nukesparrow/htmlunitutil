/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.nukesparrow.htmlunit;

import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author azazar
 */
public class SilentJavaScriptErrorListener implements JavaScriptErrorListener {

    @Override
    public void scriptException(HtmlPage page, ScriptException scriptException) {
    }

    @Override
    public void timeoutError(HtmlPage page, long allowedTime, long executionTime) {
    }

    @Override
    public void malformedScriptURL(HtmlPage page, String url, MalformedURLException malformedURLException) {
    }

    @Override
    public void loadScriptError(HtmlPage page, URL scriptUrl, Exception exception) {
    }
    
}
