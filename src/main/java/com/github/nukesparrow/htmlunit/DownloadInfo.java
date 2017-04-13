/*
 * PROPRIETARY/CONFIDENTIAL
 */
package com.github.nukesparrow.htmlunit;

import com.gargoylesoftware.htmlunit.WebRequest;
import org.apache.http.HttpResponse;

/**
 *
 * @author azazar
 */
public class DownloadInfo {
    
    public WebRequest request;
    public HttpResponse response;

    public DownloadInfo(WebRequest request, HttpResponse response) {
        this.request = request;
        this.response = response;
    }
    
}
