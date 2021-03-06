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


package com.github.wuic.servlet;

import com.github.wuic.EnumNutType;
import com.github.wuic.context.ContextBuilder;
import com.github.wuic.NutType;
import com.github.wuic.WuicFacade;
import com.github.wuic.context.HeapResolutionEvent;
import com.github.wuic.context.SimpleContextBuilderConfigurator;
import com.github.wuic.exception.WuicException;
import com.github.wuic.nut.ConvertibleNut;
import com.github.wuic.nut.HeapListener;
import com.github.wuic.nut.InMemoryNut;
import com.github.wuic.nut.NutWrapper;
import com.github.wuic.nut.NutsHeap;
import com.github.wuic.nut.dao.servlet.RequestDispatcherNutDao;
import com.github.wuic.util.FutureLong;
import com.github.wuic.util.IOUtils;
import com.github.wuic.util.NumberUtils;
import com.github.wuic.util.NutUtils;
import com.github.wuic.util.StringUtils;
import com.github.wuic.util.UrlProvider;
import com.github.wuic.util.UrlProviderFactory;
import com.github.wuic.util.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Future;

/**
 * <p>
 * This filter uses the {@link com.github.wuic.WuicFacade} to configure the related {@link ContextBuilder} with workflow
 * built on the fly when an HTML page is filtered. The created workflow uses default engines.
 * </p>
 *
 * <p>
 * This filters uses an internal {@link com.github.wuic.nut.dao.NutDao} to retrieve referenced nuts when parsing HTML.
 * By default, the DAO built from a {@link com.github.wuic.nut.dao.servlet.RequestDispatcherNutDao}. DAO is configured
 * like this for consistency reason because the version number must be computed from content when scripts are declared
 * inside tag. User can takes control over {@link com.github.wuic.nut.dao.NutDao} creation by extending this class and
 * overriding the {@link #createContextNutDaoBuilder(String, com.github.wuic.context.ContextBuilder, String)} method.
 * </p>
 *
 * <p>
 * WUIC considers that a filtered content is static by default and could be cached as any other resource. This will be
 * the cache for many applications that does not use server-side HTML generation. However, if the content is dynamic,
 * e.g it is not the same for two different users, WUIC should not cache it to serve an up-to-date response body. The
 * filter detects a dynamic content when it has been generated with a tag officially provided by WUIC (like taglib for
 * JSP or processor for Thymeleaf). In case of a dynamic content generated otherwise, you can use the init-param
 * {@link #FORCE_DYNAMIC_CONTENT} to tell WUIC to consider the content always dynamic.
 * </p>
 *
 * <p>
 * This filter supports server-hint mode. Server-hint is enabled by default. It associates to all resources that should
 * be loaded as soon as possible by the HTML page a "Link" header with "preload" rel value. Other resources are
 * associated to a "prefetch" rel value to tell the browser to download them with a low priority. Note that proxy like
 * "nghttpx" can use this header to push the resource over HTTP/2.
 * </p>
 *
 * <p>
 * Server-push is also supported. If servlet 4 is supported by the container, a {@link ServletPushService} will be used.
 * Otherwise it relies on a {@link PushService} implementation discovered in the classpath that leverage a native implementation.
 * It will be enabled by default if an implementation is found by the {@link ServiceLoader}.
 * You can disable the server-push with the {@link #DISABLE_SERVER_PUSH} init-param.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.4.4
 */
public class HtmlParserFilter extends SimpleContextBuilderConfigurator implements Filter {

    /**
     * Property that tells the filter to use or not HTTP2 server push. Server push is enabled by default.
     */
    public static final String DISABLE_SERVER_PUSH = "c.g.wuic.filter.disableServerPush";

    /**
     * An init parameter that tells the filter how to consider the filtered content dynamic or not.
     */
    public static final String FORCE_DYNAMIC_CONTENT = "c.g.wuic.forceDynamicContent";

    /**
     * If an attribute with this name is defined in the filtered request, then this filter will skip its related operation.
     */
    public static final String SKIP_FILTER = HtmlParserFilter.class.getName() + ".skip";

    /**
     * The logger.
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * {@link ContextBuilder} to use.
     */
    private ContextBuilder contextBuilder;

    /**
     * Workflow IDs and nuts generated by this filter.
     */
    private final Map<String, InMemoryNut> filterDataMap;

    /**
     * The WUIC facade.
     */
    private WuicFacade wuicFacade;

    /**
     * The root nut DAO builder ID;
     */
    private String rootNuDaoBuilderId;

    /**
     * HTTP/2 server push support. Default is server-hint.
     */
    private PushService pushService;

    /**
     * Particular setting that specifies how WUIC should check if filtered content is dynamic or not.
     */
    private boolean forceDynamicContent;

    /**
     * <p>
     * Builds a new instance with a specific {@link WuicFacade} and a root {@link com.github.wuic.nut.dao.NutDao} builder.
     * </p>
     *
     * @param wuicFacade the WUIC facade
     * @param rootNuDaoBuilderId the root nut DAO builder ID
     */
    public HtmlParserFilter(final WuicFacade wuicFacade, final String rootNuDaoBuilderId) {
        super(HtmlParserFilter.class.getName());
        this.filterDataMap = new HashMap<String, InMemoryNut>();
        this.wuicFacade = wuicFacade;
        this.rootNuDaoBuilderId = rootNuDaoBuilderId;
    }

    /**
     * <p>
     * Builds a new instance with a specific {@link WuicFacade} and the default {@link RequestDispatcherNutDao} builder
     * as root {@link com.github.wuic.nut.dao.NutDao} builder.
     * </p>
     *
     * @param wuicFacade the WUIC facade
     */
    public HtmlParserFilter(final WuicFacade wuicFacade) {
        this(wuicFacade, ContextBuilder.getDefaultBuilderId(RequestDispatcherNutDao.class));
    }

    /**
     * <p>
     * Builds a new instance. The internal {@link WuicFacade} will be initialized when {@link #init(javax.servlet.FilterConfig)}
     * method will be invoked. At this moment, this class will get the object from {@link WuicServletContextListener}.
     * In this case, the listener must be declared in th servlet container configuration.
     * </p>
     */
    public HtmlParserFilter() {
        this(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        try {
            if (wuicFacade == null) {
                wuicFacade = WuicServletContextListener.getWuicFacade(filterConfig.getServletContext());
            }

            wuicFacade.configure(this);

            configureServerPush(filterConfig);
            forceDynamicContent = "true".equals(filterConfig.getInitParameter(FORCE_DYNAMIC_CONTENT));
        } catch (WuicException we) {
            throw new ServletException(we);
        }
    }

    /**
     * <p>
     * Creates a new {@link com.github.wuic.context.ContextBuilder.ContextNutDaoBuilder} with the given {@link ContextBuilder}.
     * </p>
     *
     * @param id the ID
     * @param contextBuilder the context builder to use
     * @param rootNutDaoBuilderId the root {@link com.github.wuic.nut.dao.NutDao} builder which should be extended by the new builder
     * @return the nut DAO
     */
    protected ContextBuilder.ContextNutDaoBuilder createContextNutDaoBuilder(final String id,
                                                                             final ContextBuilder contextBuilder,
                                                                             final String rootNutDaoBuilderId) {
        try {
            return contextBuilder.cloneContextNutDaoBuilder(id, rootNutDaoBuilderId);
        } catch (IllegalArgumentException ie) {
            logger.info("Cannot clone the builder, create a new one", ie);
            return contextBuilder.contextNutDaoBuilder(id, RequestDispatcherNutDao.class);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        // Skip
        if (request.getAttribute(SKIP_FILTER) != null) {
            chain.doFilter(request, response);
            return;
        }

        request.setAttribute(HtmlParserFilter.class.getName(), Boolean.TRUE);
        final HttpServletResponse httpServletResponse = HttpServletResponse.class.cast(response);
        final InMemoryHttpServletResponseWrapper wrapper = new InMemoryHttpServletResponseWrapper(httpServletResponse);

        chain.doFilter(request, new HttpServletResponseWrapper(wrapper));
        final byte[] bytes = wrapper.toByteArray();
        final char[] chars = wrapper.toCharArray();

        // There is some content to parse
        if ((bytes != null && bytes.length > 0) || (chars != null && chars.length > 0)) {
            try {
                final HttpServletRequest httpRequest = HttpServletRequest.class.cast(request);
                final String workflowId = "W" + StringUtils.toHexString(IOUtils.digest(extractWorkflowId(httpRequest)));
                final String path = buildPath(httpRequest, httpServletResponse);
                updateWorkflow(httpRequest, bytes, chars, workflowId, path);

                final List<ConvertibleNut> nuts = wuicFacade.runWorkflow(workflowId, new ServletProcessContext(httpRequest));
                logger.info("Finding nut {} for run workflow {}", path, workflowId);
                final ConvertibleNut htmlNut = NutUtils.findByName(nuts, path);

                if (htmlNut == null) {
                    WuicException.throwBadStateException(new IllegalStateException("The filtered page has not been found in parsed result."));
                    return;
                }

                final HttpServletResponse httpResponse = HttpServletResponse.class.cast(response);
                final UrlProvider provider = getUrlProvider(httpRequest).create(IOUtils.mergePath(wuicFacade.getContextPath(), workflowId));
                final Map<String, ConvertibleNut> collectedNut = collectReferenceNut(provider, htmlNut);

                if (pushService != null) {
                    pushService.push(httpRequest, httpResponse, collectedNut.keySet());
                } else {
                    hint(collectedNut, httpResponse);
                }

                final ConvertibleNut writeNut;

                // If the content is dynamic, we force the version number to be computed on fresh content in order to not match ETag
                if (htmlNut.isDynamic()) {
                    final FutureLong versionNumber = new FutureLong(getVersionNumber(chars, bytes));
                    writeNut = new NutWrapper(htmlNut) {
                        @Override
                        public Future<Long> getVersionNumber() {
                            return versionNumber;
                        }
                    };
                } else {
                    writeNut = htmlNut;
                }

                HttpUtil.INSTANCE.write(writeNut, httpRequest, httpResponse, false);
            } catch (WuicException we) {
                logger.error("Unable to parse HTML", we);
                response.getOutputStream().print(bytes != null && bytes.length > 0 ? new String(bytes) : new String(chars));
            }
        }
    }

    /**
     * <p>
     * Initiates server-hint.
     * </p>
     *
     * @param collectedNut nuts to hint
     * @param httpResponse the response where headers will be added
     */
    private void hint(final Map<String, ConvertibleNut> collectedNut, final HttpServletResponse httpResponse) {
        // Adds to the given response all referenced nuts URLs associated to the "Link" header.
        for (final Map.Entry<String, ConvertibleNut> entry : collectedNut.entrySet()) {
            final String strategy = entry.getValue().isSubResource() ? "preload" : "prefetch";
            final String as = entry.getValue().getNutType().getHintInfo();
            httpResponse.addHeader("Link",
                    String.format("<%s>; rel=%s%s", entry.getKey(), strategy, as == null ? "" : "; as=".concat(as)));
        }
    }

    /**
     * <p>
     * Updates the workflow for the given ID or creates it if it does not exists.
     * </p>
     *
     * @param httpRequest the request
     * @param bytes the bytes, {@code null} or empty if the content is a char stream
     * @param chars the chars, {@code null} or empty if the content is a byte stream
     * @param workflowId the workflow ID
     * @param path the filtered nut path
     * @throws IOException if an I/O error occurs
     * @throws WuicException if context construction fails
     */
    private void updateWorkflow(final HttpServletRequest httpRequest,
                                final byte[] bytes,
                                final char[] chars,
                                final String workflowId,
                                final String path)
            throws IOException, WuicException {
        logger.info("Filtering content associated to workflow {}", workflowId);
        Boolean exists;

        synchronized (filterDataMap) {
            exists = filterDataMap.containsKey(workflowId);
        }

        if (!exists) {
            final InMemoryNut nut = configureBuilder(httpRequest, path, contextBuilder, workflowId, bytes, chars);

            synchronized (filterDataMap) {
                filterDataMap.put(workflowId, nut);
            }
        } else {
            synchronized (filterDataMap) {
                final InMemoryNut nut = filterDataMap.get(workflowId);

                if (nut.isDynamic()) {
                    if (bytes != null && bytes.length > 0) {
                        nut.setByteArray(bytes);
                    } else {
                        nut.setCharArray(chars);
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Configures the server push support retarding the {@link #DISABLE_SERVER_PUSH} setting.
     * </p>
     *
     * @param filterConfig the filter config instance
     */
    private void configureServerPush(final FilterConfig filterConfig) {
        final String sp = filterConfig.getInitParameter(DISABLE_SERVER_PUSH);

        if (sp == null || "true".equals(sp)) {
            logger.info("HTTP/2 server push is enabled. Discovering server push support in the classpath...");

            final ServiceLoader<PushService> serviceLoader = ServiceLoader.load(PushService.class);

            for (final PushService ps : serviceLoader) {
                if (pushService == null) {
                    pushService = ps;
                    logger.info("Server push support '{}' has been installed.", pushService);
                } else {
                    logger.warn("Duplicate server push support: '{}' has been found but '{}' is already installed."
                            + " This support will be ignored.", ps.getClass().getName(), pushService.getClass().getName());
                }
            }

            if (pushService == null) {
                if (filterConfig.getServletContext().getMajorVersion() >= NumberUtils.FOUR) {
                    logger.info("Using Servlet 4 push support.");
                    pushService = new ServletPushService();
                } else {
                    logger.info("No HTTP/2 server push support found! No resource will be pushed.");
                }
            }
        } else {
            logger.info("HTTP/2 server push is disabled, no resource will be pushed.");
        }
    }

    /**
     * <p>
     * Retrieves an optional {@link UrlProviderFactory} instance registered in request's attributes.
     * </p>
     *
     * @param httpServletRequest the request
     * @return the factory bound to the request, {@link com.github.wuic.util.UrlUtils.DefaultUrlProviderFactory} otherwise
     */
    private UrlProviderFactory getUrlProvider(final HttpServletRequest httpServletRequest) {
        final Object attribute = httpServletRequest.getAttribute(UrlProviderFactory.class.getName());
        return attribute != null ? UrlProviderFactory.class.cast(attribute) : new UrlUtils.DefaultUrlProviderFactory();
    }

    /**
     * <p>
     * Adds recursively all URLs from referenced nuts of the given nut in the returned list
     * </p>
     *
     * @param urlProvider the provider
     * @param nut the referenced nuts owner
     * @return a map of all collected URLs associated to the nut
     */
    private Map<String, ConvertibleNut> collectReferenceNut(final UrlProvider urlProvider, final ConvertibleNut nut) {
        if (nut.getReferencedNuts() != null && !nut.getReferencedNuts().isEmpty()) {
            final Map<String, ConvertibleNut> retval = new HashMap<String, ConvertibleNut>();

            for (final ConvertibleNut ref : nut.getReferencedNuts()) {
                final String url = urlProvider.getUrl(ref);
                retval.put(url.startsWith("/") ? url : '/' + url, ref);
                retval.putAll(collectReferenceNut(urlProvider, ref));
            }

            return retval;
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * <p>
     * Builds the path of the filtered nut from the servlet path for cache purpose.
     * </p>
     *
     * @param request the request
     * @param response the response
     * @return the nut path
     * @throws WuicException if {@code NutTypeFactory} can't be retrieved
     */
    private String buildPath(final HttpServletRequest request, final HttpServletResponse response) throws WuicException {
        final StringBuilder workflowBuilder = new StringBuilder();
        workflowBuilder.append(request.getServletPath().substring(1));

        final NutType nutType = wuicFacade.getNutTypeFactory().getNutTypeForMimeType(response.getContentType());

        // Check that key ends with valid extension
        if (nutType == null) {
            logger.warn(String.format("%s is not a supported mime type. URI must ends with a supported extension.", response.getContentType()));
        } else {
            for (final String ext : nutType.getExtensions()) {
                final int index = workflowBuilder.lastIndexOf(ext);

                // Good extension already set
                if (workflowBuilder.length() - ext.length() == index) {
                    return workflowBuilder.toString();
                }
            }

            // No valid extension set, force one
            workflowBuilder.append(nutType.getExtensions()[0]);
        }

        return workflowBuilder.toString();
    }

    /**
     * <p>
     * Creates a {@code Long} value representing a version number computed from the given byte array if not {@code null}
     * and not empty. If it's the case, the char array specified in parameter will be used.
     * </p>
     *
     * @param chars the chars
     * @param bytes the bytes
     * @return the version number compute from the correct array
     */
    private Long getVersionNumber(final char[] chars, final byte[] bytes) {
        return ByteBuffer.wrap(bytes != null && bytes.length > 0 ?
                IOUtils.digest(bytes) : IOUtils.digest(IOUtils.toBytes(Charset.defaultCharset(), chars))).getLong();
    }

    /**
     * <p>
     * Indicates if the content returned for the given request is dynamic or not according to the filter configuration
     * and the request state.
     * </p>
     *
     * @param request the request
     * @return {@code true} if the response content is dynamic, {@code false} otherwise
     */
    private boolean isDynamic(final HttpServletRequest request) {
        return forceDynamicContent || request.getAttribute(FORCE_DYNAMIC_CONTENT) != null;
    }

    /**
     * <p>
     * Extracts the workflow ID from the HTTP request.
     * </p>
     *
     * <p>
     * The method can return any array of byte array containing all the data that represent an unique ID for the filtered page.
     * By default, {@link javax.servlet.http.HttpServletRequest#getServletPath()} is used but if many pages serve different
     * statics and share the save servlet path, this method can be overridden to indicate more data, for instance the parameter
     * values.
     * </p>
     *
     * @param httpRequest the HTTP request associated to the returned workflow ID
     * @return the ID
     */
    protected byte[][] extractWorkflowId(final HttpServletRequest httpRequest) {
        return new byte[][] { httpRequest.getServletPath().getBytes(), };
    }

    /**
     * <p>
     * Configures the workflow corresponding to the given ID in the specified context builder.
     * </p>
     *
     * @param request the request
     * @param path the filtered nut path
     * @param contextBuilder the builder
     * @param workflowId the workflow ID
     * @param bytes the bytes, {@code null} or empty if the content is a char stream
     * @param chars the chars, {@code null} or empty if the content is a byte stream
     * @return the created nut
     * @throws WuicException if context can't be refreshed
     * @throws IOException if any I/O error occurs
     */
    protected InMemoryNut configureBuilder(final HttpServletRequest request,
                                            final String path,
                                            final ContextBuilder contextBuilder,
                                            final String workflowId,
                                            final byte[] bytes,
                                            final char[] chars)
            throws IOException, WuicException {
        final Long versionNumber = getVersionNumber(chars, bytes);
        final InMemoryNut retval;

        if (bytes != null && bytes.length > 0) {
            retval = new InMemoryNut(bytes, path, wuicFacade.getNutTypeFactory().getNutType(EnumNutType.HTML), versionNumber, isDynamic(request));
        } else {
            retval = new InMemoryNut(chars, path, wuicFacade.getNutTypeFactory().getNutType(EnumNutType.HTML), versionNumber, isDynamic(request));
        }

        // The workflow could already exists
        // It could have been initialized by this method or somewhere else
        // TODO: currently only the filtered nut has a chance to be dynamic only if the workflow is created from this method
        if (!wuicFacade.workflowIds().contains(workflowId)) {
            try {
                createContextNutDaoBuilder(workflowId, contextBuilder.tag(getClass()), rootNuDaoBuilderId)
                    .proxyPathForNut(path, retval)
                    .toContext()
                    .disposableHeap(workflowId, workflowId, new String[]{path}, new HeapListener() {
                        @Override
                        public void nutUpdated(final NutsHeap heap) {
                            synchronized (filterDataMap) {
                                filterDataMap.remove(workflowId);
                            }
                        }

                        @Override
                        public void heapResolved(final HeapResolutionEvent event) {
                            // ignore
                        }
                    });
            } finally {
                contextBuilder.releaseTag();
            }

            contextBuilder.build();
            wuicFacade.refreshContext();
        }

        return retval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int internalConfigure(final ContextBuilder ctxBuilder) {
        contextBuilder = ctxBuilder;
        return -1;
    }
}