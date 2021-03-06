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

/**
 *
 * @author Nuke Sparrow <nukesparrow@bitmessage.ch>
 */
public class HUQueryException extends RuntimeException {

    /**
     * Creates a new instance of <code>HUQueryException</code> without detail
     * message.
     */
    public HUQueryException() {
    }

    /**
     * Constructs an instance of <code>HUQueryException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public HUQueryException(String msg) {
        super(msg);
    }

    public HUQueryException(String message, Throwable cause) {
        super(message, cause);
    }

    public HUQueryException(Throwable cause) {
        super(cause);
    }

}
