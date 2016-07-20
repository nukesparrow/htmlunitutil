/*
 * Copyright 2016 Nuke Sparrow <nukesparrow@bitmessage.ch>.
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

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import com.github.nukesparrow.htmlunit.UrlTextUtil;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class UtilTextUtilTest {
    
    private static void testExtractWords(String s, String[] l) {
        assertArrayEquals(l, UrlTextUtil.extractWords(s));
    }

    private static void testExtractWords(String s) {
        testExtractWords(s.replaceAll(",", ""), s.split(","));
    }
    
    @Test
    public void testExtractWords() {
        testExtractWords("asd,Asd");
        testExtractWords("asd,123,Asd");
        testExtractWords("asd,123,Asd,Asd,ASDzxc");
        testExtractWords("/test/Asd/asdAsd?", "test,Asd,asd,Asd".split(","));
        testExtractWords("/test/Asd/asdAsd?asd", "test,Asd,asd,Asd,asd".split(","));
    }
    
}
