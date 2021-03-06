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


package com.github.wuic.context;

import com.github.wuic.ProcessContext;

import java.io.IOException;

/**
 * <p>
 * A simple {@link ContextBuilderConfigurator} that can't be polled and identified with a {@code String} tag.
 * The {@link #getProcessContext()} method will return {@link ProcessContext#DEFAULT}.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.5.3
 */
public class SimpleContextBuilderConfigurator extends ContextBuilderConfigurator {

    /**
     * The tag.
     */
    private final String tag;

    /**
     * <p>
     * Builds a new instance.
     * </p>
     *
     * @param tag the tag
     */
    public SimpleContextBuilderConfigurator(final String tag) {
        this.tag = tag;
    }

    /**
     * <p>
     * Builds a new instance with the class name as tag.
     * </p>
     */
    public SimpleContextBuilderConfigurator() {
        this(SimpleContextBuilderConfigurator.class.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Long getLastUpdateTimestampFor(final String path) throws IOException {
        return -1L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTag() {
        return tag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int internalConfigure(final ContextBuilder ctxBuilder) {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessContext getProcessContext() {
        return ProcessContext.DEFAULT;
    }
}
