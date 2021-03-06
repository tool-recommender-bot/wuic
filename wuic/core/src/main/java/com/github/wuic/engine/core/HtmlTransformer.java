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


package com.github.wuic.engine.core;

import com.github.wuic.EnumNutType;
import com.github.wuic.NutType;
import com.github.wuic.engine.EngineRequest;
import com.github.wuic.engine.EngineType;
import com.github.wuic.nut.InMemoryNut;
import com.github.wuic.nut.ConvertibleNut;
import com.github.wuic.nut.dao.core.ProxyNutDao;
import com.github.wuic.nut.filter.NutFilter;
import com.github.wuic.util.CollectionUtils;
import com.github.wuic.util.IOUtils;
import com.github.wuic.util.Input;
import com.github.wuic.util.NumberUtils;
import com.github.wuic.util.NutUtils;
import com.github.wuic.util.Output;
import com.github.wuic.util.Pipe;
import com.github.wuic.util.UrlProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * This class is a transformer that appends to a particular nut an extracted resource added as a referenced nut.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.5.0
 */
public final class HtmlTransformer implements Serializable, Pipe.Transformer<ConvertibleNut> {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The request. This transformer should not be serialized before a first call to
     * {@link #transform(Input, Output, ConvertibleNut)} is performed.
     */
    private final transient EngineRequest request;

    /**
     * The nut filters.
     */
    private final transient List<NutFilter> nutFilters;

    /**
     * Charset.
     */
    private final String charset;

    /**
     * Server hint activation.
     */
    private final boolean serverHint;

    /**
     * The parser service.
     */
    private final AssetsMarkupParser parser;

    /**
     * All replacements performed by the transformer.
     */
    private Map<Object, List<String>> replacements;

    /**
     * <p>
     * Builds a new instance.
     * </p>
     *
     * @param r the request
     * @param cs the charset
     * @param sh server hint
     * @param nutFilterList the nut filters
     * @param p the parser service
     */
    HtmlTransformer(final EngineRequest r,
                    final String cs,
                    final boolean sh,
                    final List<NutFilter> nutFilterList,
                    final AssetsMarkupParser p) {
        this.request = r;
        this.charset = cs;
        this.serverHint = sh;
        this.nutFilters = nutFilterList;
        this.parser = p;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean transform(final Input is, final Output os, final ConvertibleNut convertible)
            throws IOException {
        final Pipe.Execution e = is.execution();
        final String content = e.isText()
                ? new String(e.getCharResult()) : new String(IOUtils.toChars(Charset.forName(charset), e.getByteResult()));
        final StringBuilder transform = new StringBuilder(content);

        // Normalize linefeed
        int index = 0;

        while ((index = transform.indexOf("\r\n", index)) != -1) {
            transform.deleteCharAt(index);
        }

        int end = 0;

        // Perform cached replacement
        if (this.replacements != null) {
            for (final Map.Entry<Object, List<String>> entry : this.replacements.entrySet()) {
                final Object replacement = entry.getKey();
                end = replace(transform, replacement, entry.getValue(), end);
            }
        } else {
            this.replacements = new LinkedHashMap<Object, List<String>>();
            final int endParent = convertible.getName().lastIndexOf('/');
            final String rootPath = endParent == -1 ? "" : convertible.getName().substring(0, endParent);
            final ProxyNutDao proxy = new ProxyNutDao(rootPath, request.getHeap().findDaoFor(convertible));
            final HtmlInspectorEngine.Handler h = new HtmlInspectorEngine.Handler(content, convertible.getName(), rootPath, proxy, request, parser, nutFilters);
            final List<HtmlInspectorEngine.ParseInfo> parseInfoList = h.getParseInfoList();
            final List<ConvertibleNut> referenced = new ArrayList<ConvertibleNut>();

            final String prefix = IOUtils.mergePath(request.getContextPath(), request.getWorkflowId());
            final UrlProvider urlProvider = request.getUrlProviderFactory().create(prefix);

            // A workflow have been created for each heap
            for (final HtmlInspectorEngine.ParseInfo parseInfo : parseInfoList) {
                // Perform replacement
                final String replacement = parseInfo.replacement(request, urlProvider, referenced, convertible);
                end = replace(transform, replacement, parseInfo.getCapturedStatements(), end);
                this.replacements.put(replacement, parseInfo.getCapturedStatements());
            }

            for (final ConvertibleNut ref : referenced) {
                convertible.addReferencedNut(ref);
            }

            // Modify the content to give more information to the client directly inside the page
            if (!request.isStaticsServedByWuicServlet()) {
                final ConvertibleNut appCache = applicationCache(convertible, urlProvider, transform);

                if (appCache != null) {
                    referenced.add(appCache);
                }

                // The hint resource
                if (serverHint) {
                    hintResources(urlProvider, transform, referenced);
                }
            }
        }

        os.writer().write(transform.toString());

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canAggregateTransformedStream() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int order() {
        return EngineType.INSPECTOR.ordinal();
    }

    /**
     * <p>
     * Inserts in the given HTML content a application cache file as an attribute in the "html" tag.
     * If the tag is missing, nothing is done.
     * </p>
     *
     * @param nut the nut representing the HTML page
     * @param urlProvider the URL provider
     * @param content the page content
     * @return  the created {@code appcache} nut, {@code null} if no {@code html} tag exists in the content
     */
    private ConvertibleNut applicationCache(final ConvertibleNut nut,
                                            final UrlProvider urlProvider,
                                            final StringBuilder content) {
        int index = content.indexOf("<html");

        if (index == -1) {
            logger.warn("Filtered HTML does not have any <html>. Application cache file won't be inserted.");
            return null;
        } else {
            // Compute content
            final StringBuilder sb = new StringBuilder();
            final List<ConvertibleNut> cached = CollectionUtils.newList(nut);
            collect(urlProvider, sb, cached);
            final Long versionNumber = NutUtils.getVersionNumber(cached);
            sb.append(IOUtils.NEW_LINE).append("NETWORK:").append(IOUtils.NEW_LINE).append("*");
            sb.insert(0, String.format("CACHE MANIFEST%s# Version number: %d", IOUtils.NEW_LINE, versionNumber));

            // Create the nut
            final String name = nut.getName().concat(".appcache");
            final byte[] bytes = sb.toString().getBytes();
            final NutType nutType = request.getNutTypeFactory().getNutType(EnumNutType.APP_CACHE);
            final ConvertibleNut appCache = new InMemoryNut(bytes, name, nutType, versionNumber, false);
            nut.addReferencedNut(appCache);

            // Modify the HTML content
            index += NumberUtils.FIVE;
            final String replacement = String.format(" manifest=\"%s\"", urlProvider.getUrl(appCache));
            content.insert(index, replacement);
            this.replacements.put(index, Arrays.asList(replacement));
            return appCache;
        }
    }

    /**
     * <p>
     * Creates nut representing an 'appcache' for the given nuts.
     * </p>
     *
     * @param urlProvider the URL provider
     * @param sb the string builder where cached nuts URL will be added
     * @param nuts the nuts to put in cache
     */
    private void collect(final UrlProvider urlProvider, final StringBuilder sb, final List<ConvertibleNut> nuts) {
        for (final ConvertibleNut nut : nuts) {
            sb.append(IOUtils.NEW_LINE).append(urlProvider.getUrl(nut));

            if (nut.getReferencedNuts() != null) {
                collect(urlProvider, sb, nut.getReferencedNuts());
            }
        }
    }

    /**
     * <p>
     * Inserts in the given HTML content all resources hint computed from the given nuts. The "link" tag will be
     * inserted in a "head" tag that could be created in it does not exists. Nothing will be done if the given
     * content does not contain any "html" tag.
     * </p>
     *
     * @param urlProvider the provider
     * @param content the content
     * @param convertibleNuts the nuts
     */
    private void hintResources(final UrlProvider urlProvider,
                               final StringBuilder content,
                               final List<ConvertibleNut> convertibleNuts) {
        int index = content.indexOf("<head>");

        if (index == -1) {
            index = content.indexOf("<head ");
        }

        if (index == -1) {
            index = content.indexOf("<html>");

            if (index == -1) {
                index = content.indexOf("<html ");
            }

            if (index == -1) {
                logger.warn("Filtered HTML does not have any <html>. Server hint directives won't be inserted.");
                return;
            } else {
                // Closing <html> tag
                index = content.indexOf(">", index) + 1;
                content.insert(index, "<head></head>");
                index += NumberUtils.FIVE;
            }
        } else {
            // Closing <head> tag
            index = content.indexOf(">", index);
        }

        final StringBuilder hints = new StringBuilder();
        appendHint(urlProvider, hints, convertibleNuts);
        index++;
        final String replacement = hints.toString();
        content.insert(index, replacement);
        this.replacements.put(index, Arrays.asList(replacement));
    }

    /**
     * <p>
     * Appends recursively to the given builder all the hints corresponding to each nut specified in parameter
     * </p>
     *
     * @param urlProvider the URL provider
     * @param builder the builder
     * @param convertibleNuts the resource hints
     */
    private void appendHint(final UrlProvider urlProvider,
                            final StringBuilder builder,
                            final List<ConvertibleNut> convertibleNuts) {
        for (final ConvertibleNut ref : convertibleNuts) {
            final NutType nutType = ref.getNutType();
            final String as = nutType.getHintInfo() == null ? "" : " as=\"" + nutType.getHintInfo() + "\"";
            final String strategy = ref.isSubResource() ? "preload" : "prefetch";
            builder.append(String.format("<link rel=\"%s\" href=\"%s\"%s />%s", strategy, urlProvider.getUrl(ref), as, IOUtils.NEW_LINE));

            if (ref.getReferencedNuts() != null) {
                appendHint(urlProvider, builder, ref.getReferencedNuts());
            }
        }
    }

    /**
     * <p>
     * Replaces in the given {@link StringBuilder} all the statements specified in parameter by an empty
     * {@code String} except the first one which will be replaced by a particular replacement also specified
     * in parameter.
     * </p>
     *
     * @param transform the builder
     * @param replacement the replacement
     * @param statements the statements to replace
     * @param startIndex the index where the method could start to search statements in the builder
     * @return the updated index
     */
    private int replace(final StringBuilder transform,
                        final Object replacement,
                        final List<String> statements,
                        final int startIndex) {
        if (replacement instanceof Integer) {
            final int index = Integer.class.cast(replacement);

            // Insert all captured statements at the given index
            for (final String statement : statements) {
                transform.insert(index, statement);
            }

            return startIndex;
        } else {
            int end = startIndex;

            // Replace all captured statements with HTML generated from WUIC process
            for (int i = 0; i < statements.size(); i++) {
                final String toReplace = statements.get(i);
                end = replace(transform, replacement.toString(), toReplace, end, i == 0);
            }

            return end;
        }
    }

    /**
     * <p>
     * Replaces in the given {@link StringBuilder} the statement specified in parameter by an empty {@code String}
     * except if the statement is the first one which will be replaced by a particular replacement also specified
     * in parameter.
     * </p>
     *
     * @param transform the builder
     * @param replacement the replacement
     * @param startIndex the index where the method could start to search statements in the builder
     * @param toReplace the string to replace
     * @param isFirst if the given statement is the first one
     * @return the updated index
     */
    private int replace(final StringBuilder transform,
                        final String replacement,
                        final String toReplace,
                        final int startIndex,
                        final boolean isFirst) {

        final int start = transform.indexOf(toReplace, startIndex);
        int end = start + toReplace.length();

        // Add the WUIC result in place of the first statement
        if (isFirst) {
            transform.replace(start, end, replacement);
            end = start + replacement.length();
        } else {
            transform.replace(start, end, "");
            end = start;
        }

        return end;
    }
}
