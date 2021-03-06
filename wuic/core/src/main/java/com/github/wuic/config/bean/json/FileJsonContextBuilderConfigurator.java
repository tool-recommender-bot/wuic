/*
 * Copyright (c) 2016   The authors of WUIC
 *
 * License/Terms of Use
 * Permission is hereby granted, free of charge and for the term of intellectual
 * property rights on the Software, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to use, copy, modify and
 * propagate free of charge, anywhere in the world, all or part of the Software
 * subject to the following mandatory conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, PEACEFUL ENJOYMENT,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */


package com.github.wuic.config.bean.json;

import com.github.wuic.exception.WuicException;
import com.github.wuic.util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;

/**
 * <p>
 * Represents a configurator based on JSON read from an {@link java.net.URL}. Polling is supported thanks to the modification date
 * provided by the {@link java.net.URL} object.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.5.3
 */
public class FileJsonContextBuilderConfigurator extends ReaderJsonContextBuilderConfigurator {

    /**
     * The {@link java.net.URL} pointing to the wuic.json file.
     */
    private URL jsonFile;

    /**
     * <p>
     * Creates a new instance.
     * </p>
     *
     * @param wuicJson the wuic.json file URL
     * @throws IOException if file can't be read
     */
    public FileJsonContextBuilderConfigurator(final URL wuicJson) throws IOException {
        super(check(wuicJson).toString());
        jsonFile = wuicJson;
    }

    /**
     * <p>
     * Checks the given URL is not {@code null}. If {@code null} an exception is thrown.
     * </p>
     *
     * @param url the URL
     * @return the checked URL
     */
    private static URL check(final URL url) {
        if (url == null) {
            WuicException.throwWuicJsonReadException(new IllegalArgumentException("JSON configuration URL for WUIC is null"));
        }

        return url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Reader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(jsonFile.openStream()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Long getLastUpdateTimestampFor(final String path) throws IOException {
        InputStream is = null;
        try {
            final URLConnection c = jsonFile.openConnection();
            is = c.getInputStream();
            return c.getLastModified();
        } finally {
            IOUtils.close(is);
        }
    }
}
