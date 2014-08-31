/*
 * "Copyright (c) 2014   Capgemini Technology Services (hereinafter "Capgemini")
 *
 * License/Terms of Use
 * Permission is hereby granted, free of charge and for the term of intellectual
 * property rights on the Software, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to use, copy, modify and
 * propagate free of charge, anywhere in the world, all or part of the Software
 * subject to the following mandatory conditions:
 *
 * -   The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Any failure to comply with the above shall automatically terminate the license
 * and be construed as a breach of these Terms of Use causing significant harm to
 * Capgemini.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, PEACEFUL ENJOYMENT,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Capgemini shall not be used in
 * advertising or otherwise to promote the use or other dealings in this Software
 * without prior written authorization from Capgemini.
 *
 * These Terms of Use are subject to French law.
 *
 * IMPORTANT NOTICE: The WUIC software implements software components governed by
 * open source software licenses (BSD and Apache) of which CAPGEMINI is not the
 * author or the editor. The rights granted on the said software components are
 * governed by the specific terms and conditions specified by Apache 2.0 and BSD
 * licenses."
 */


package com.github.wuic.util;

import com.github.wuic.nut.Nut;

/**
 * <p>
 * Utility class for URLs management around {@link Nut nuts}.
 * </p>
 *
 * @author Guillaume DROUET
 * @version 1.0
 * @since 0.5.0
 */
public final class UrlUtils {

    /**
     * <p>
     * Provides the default mechanism to build URL.
     * </p>
     *
     * @author Guillaume DROUET
     * @version 1.0
     * @since 0.5.0
     */
    private static final class DefaultUrlProvider implements UrlProvider {

        /**
         * The workflow ID and base path.
         */
        private String workflowContextPath;

        /**
         * <p>
         * Builds a new instance.
         * </p>
         *
         * @param wcp the workflow ID and base path
         */
        private DefaultUrlProvider(final String wcp) {
            workflowContextPath = wcp;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getUrl(final Nut nut) {
            if (nut.getProxyUri() != null) {
                return nut.getProxyUri();
            } else if (nut.getName().startsWith("http://")) {
                return nut.getName();
            } else {
                return IOUtils.mergePath(workflowContextPath, String.valueOf(NutUtils.getVersionNumber(nut)), nut.getName());
            }
        }
    }

    /**
     * <p>
     * Creates a default {@link UrlProvider}.
     * </p>
     *
     * @param workflowContextPath the workflow ID and base path
     * @return the {@link DefaultUrlProvider}
     */
    public static UrlProvider urlProvider(final String workflowContextPath) {
        return new DefaultUrlProvider(workflowContextPath);
    }

    /**
     * <p>
     * Builds a new {@link UrlMatcher}.
     * </p>
     *
     * @param requestUrl the URL to match
     * @return the matcher
     */
    public static UrlMatcher urlMatcher(final String requestUrl) {
        return new UrlMatcher(requestUrl);
    }

}