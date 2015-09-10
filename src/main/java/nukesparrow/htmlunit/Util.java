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
package nukesparrow.htmlunit;

import com.gargoylesoftware.htmlunit.WebResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
class Util {
    
    public static MapBuilder mapBuilder() {
        return new MapBuilder();
    }

    public static MapBuilder mapBuilderBaseData() {
        MapBuilder b = new MapBuilder().put("time", System.currentTimeMillis());
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            sb.append(e.toString()).append('\n');
        }
        b.put("stack", sb.toString());
        return b;
    }

    public static class MapBuilder {
        
        public final Map map = new LinkedHashMap();
        
        public MapBuilder put(Object k, Object v) {
            map.put(k, v);
            return this;
        }
        
        public MapBuilder putNotNull(Object k, Object v) {
            if (v != null)
                map.put(k, v);
            return this;
        }
        
    }
    
    public static String toDataUrl(WebResponse response) throws IOException {
        StringBuilder b = new StringBuilder("data:").append(response.getContentType());
        if (response.getContentCharsetOrNull() != null && !response.getContentType().contains("charset=")) {
            b.append(";charset=").append(response.getContentCharset());
        }
        b.append(";base64,");
        try (InputStream in = response.getContentAsStream()) {
            b.append(Base64.encodeBase64String(IOUtils.toByteArray(in)));
        }
        return b.toString();
    }

}
