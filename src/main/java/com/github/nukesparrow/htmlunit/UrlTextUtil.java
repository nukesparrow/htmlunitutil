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

import java.util.ArrayList;

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class UrlTextUtil {
    
    public static String[] extractWords(String s) {
        ArrayList<String> words = new ArrayList<>();
        
        StringBuilder b = new StringBuilder();
        char prev = 0;
        
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                if (
                    (!(Character.isLowerCase(c) && Character.isUpperCase(prev))) &&
                    ((Character.isUpperCase(c) && !Character.isUpperCase(prev)) ||
                    (Character.getType(c) != Character.getType(prev)))
                ) {

                    if (b.length() > 0) {
                        words.add(b.toString());
                        b.setLength(0);
                    }
                }
                b.append(c);
            }
            else {
                if (b.length() > 0) {
                    words.add(b.toString());
                    b.setLength(0);
                }
            }
            
            prev = c;
        }
        
        if (b.length() > 0)
            words.add(b.toString());
        
        return words.toArray(new String[0]);
    }

}
