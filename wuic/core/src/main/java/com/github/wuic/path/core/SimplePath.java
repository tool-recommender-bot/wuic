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


package com.github.wuic.path.core;

import com.github.wuic.path.DirectoryPath;
import com.github.wuic.path.Path;
import com.github.wuic.util.IOUtils;

/**
 * <p>
 * Simple implementation of a {@link com.github.wuic.path.Path}.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.3.4
 */
public abstract class SimplePath implements Path {

    /**
     * The name.
     */
    private String name;

    /**
     * The parent.
     */
    private DirectoryPath parent;

    /**
     * The charset used to read text stream.
     */
    private String charset;

    /**
     * <p>
     * Builds a new instance based on a given name and a parent directory.
     * </p>
     *
     * @param cs the charset
     * @param n the name
     * @param dp the parent
     */
    public SimplePath(final String n, final DirectoryPath dp, final String cs) {
        name = n;
        parent = dp;
        charset = cs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DirectoryPath getParent() {
        return parent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbsolutePath() {
        final StringBuilder retval = new StringBuilder();

        // Append parent path until we reach the root path
        for (Path p = getParent(); p != null; p = p.getParent()) {
            if (!(retval.length() > 0 && String.valueOf(retval.charAt(0)).equals(IOUtils.STD_SEPARATOR))
                    && !p.getName().startsWith(IOUtils.STD_SEPARATOR)) {
                retval.insert(0, IOUtils.STD_SEPARATOR);
            }

            retval.insert(0, p.getName());
        }

        return retval.append(getName()).toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * <p>
     * Gets the charset of this file path.
     * </p>
     *
     * @return the charset
     */
    public String getCharset() {
        return charset;
    }
}
