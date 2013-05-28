/*
 * "Copyright (c) 2013   Capgemini Technology Services (hereinafter "Capgemini")
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


package com.github.wuic.engine;

import com.github.wuic.FilesGroup;
import com.github.wuic.resource.WuicResource;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 * This class represents a request to be indicated to an engine.
 * </p>
 *
 * <p>
 * The user which invokes {@link Engine#parse(EngineRequest)} should indicates in the parameter the resources
 * to be parsed and the context path to use to expose the generated resources.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.3.0
 * @version 1.1
 */
public class EngineRequest {

    /**
     * The resources.
     */
    private List<WuicResource> resources;

    /**
     * The context path.
     */
    private String contextPath;

    /**
     * The group.
     */
    private FilesGroup group;

    /**
     * <p>
     * Builds a new {@code EngineRequest} with some resources specific and a specified context path to be used.
     * </p>
     *
     * @param res the resources to be parsed
     * @param cp the context root where the generated resources should be exposed
     * @param g the group
     */
    public EngineRequest(final List<WuicResource> res, final String cp, final FilesGroup g) {
        resources = res;
        contextPath = cp;
        group = g;
    }

    /**
     * <p>
     * Builds a new {@code EngineRequest} with some resources and a specified context path to be used.
     * </p>
     *
     * @param cp the context root where the generated resources should be exposed
     * @param g the group
     * @throws IOException if an I/O error occurs while getting the resources from the group
     */
    public EngineRequest(final String cp, final FilesGroup g) throws IOException {
        resources = g.getResources();
        contextPath = cp;
        group = g;
    }

    /**
     * <p>
     * Gets the resources.
     * </p>
     *
     * @return the resources
     */
    public final List<WuicResource> getResources() {
        return resources;
    }

    /**
     * <p>
     * Gets the context path.
     * </p>
     *
     * @return the context path
     */
    public final String getContextPath() {
        return contextPath;
    }

    /**
     * <p>
     * Returns the group.
     * </p>
     *
     * @return the group
     */
    public final FilesGroup getGroup() {
        return group;
    }
}
