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


package com.github.wuic.nut.dao.core;

import com.github.wuic.NutType;
import com.github.wuic.ProcessContext;
import com.github.wuic.exception.WuicException;
import com.github.wuic.nut.AbstractNutDao;
import com.github.wuic.nut.FilePathNut;
import com.github.wuic.nut.Nut;
import com.github.wuic.path.DirectoryPath;
import com.github.wuic.path.FilePath;
import com.github.wuic.path.Path;
import com.github.wuic.util.IOUtils;
import com.github.wuic.util.Input;
import com.github.wuic.util.TemporaryFileManager;
import com.github.wuic.util.TemporaryFileManagerHolder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>
 * A {@link com.github.wuic.nut.dao.NutDao} implementation for accesses based on the path API provided by WUIC.
 * </p>
 *
 * <p>
 * The class is abstract and asks subclass to define the way the base directory should be defined.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.4.2
 */
public abstract class PathNutDao extends AbstractNutDao implements TemporaryFileManagerHolder {

    /**
     * Base directory where the protocol has to look up.
     */
    private DirectoryPath baseDirectory;

    /**
     * {@code true} if the path is a regex, {@code false} otherwise.
     */
    private Boolean regularExpression;

    /**
     * {@code true} if the path is a wildcard, {@code false} otherwise.
     */
    private Boolean wildcardExpression;

    /**
     * The temporary file manager.
     */
    private TemporaryFileManager temporaryFileManager;

    /**
     * <p>
     * Initializes a new instance with a base directory.
     * </p>
     *
     * @param base the directory where we have to look up
     * @param pollingSeconds the interval for polling operations in seconds (-1 to deactivate)
     * @param proxies the proxies URIs in front of the nut
     * @param regex if the path should be considered as a regex or not
     * @param wildcard if the path should be considered as a wildcard or not
     */
    public void init(final String base,
                     final String[] proxies,
                     final int pollingSeconds,
                     final Boolean regex,
                     final Boolean wildcard) {
        init(base, proxies, pollingSeconds);

        if (regex && wildcard) {
            WuicException.throwBadArgumentException(new IllegalArgumentException("You can't set to true both wildcard and regex settings."));
        }

        regularExpression = regex;
        wildcardExpression = wildcard;
    }

    /**
     * <p>
     * Indicates if paths are a regular expression.
     * </p>
     *
     * @return {@code true} if regex is used, {@code false} otherwise
     */
    public Boolean getRegularExpression() {
        return regularExpression;
    }

    /**
     * <p>
     * Indicates if paths are a wildcard expression.
     * </p>
     *
     * @return {@code true} if wildcard is used, {@code false} otherwise
     */
    public Boolean getWildcardExpression() {
        return wildcardExpression;
    }

    /**
     * <p>
     * Gets the temporary file manager.
     * </p>
     *
     * @return the {@code TemporaryFileManager}
     */
    public final TemporaryFileManager getTemporaryFileManager() {
        return temporaryFileManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setTemporaryFileManager(final TemporaryFileManager temporaryFileManager) {
        this.temporaryFileManager = temporaryFileManager;
    }

    /**
     * <p>
     * Gets the beginning of path to be skipped during research. Should be overridden by subclass.
     * </p>
     *
     * @return the list of beginning paths
     */
    protected List<String> skipStartsWith() {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listNutsPaths(final String pattern) throws IOException {
        init();

        if (regularExpression) {
            return IOUtils.listFile(baseDirectory, Pattern.compile(pattern), skipStartsWith());
        } else if (wildcardExpression) {
            final int index = pattern.indexOf('*');

            if (index != -1) {
                final String endWith = pattern.substring(index + 1);
                if (index == 0) {
                    return IOUtils.listFile(baseDirectory, "", endWith, skipStartsWith());
                } else {
                    final String basePath = pattern.substring(0, index);
                    return IOUtils.listFile(DirectoryPath.class.cast(baseDirectory.getChild(basePath)), basePath, endWith, skipStartsWith());
                }
            }
        }

        // Checks if single path is skipped
        for (final String skipStartsWith : skipStartsWith()) {
            if (pattern.startsWith(skipStartsWith)) {
                return Collections.emptyList();
            }
        }

        // Checks if the single path exists
        DirectoryPath parent = baseDirectory;
        final String[] lookup = (pattern.startsWith("/") ? pattern.substring(1) : pattern).split("/");

        // Try to walk through the path
        for (int i = 0; i < lookup.length; i++) {
            final String l = lookup[i];

            // No child, stop searching
            if (!Arrays.asList(parent.list()).contains(l)) {
                break;
            }

            final Path p = parent.getChild(l);

            // Child is a directory, will see in next iteration if path is still valid
            if (p instanceof DirectoryPath) {
                parent = DirectoryPath.class.cast(p);
            } else if (i != lookup.length - 1) {
                // Child is not a directory and is not the end of the path: no match
                return Collections.emptyList();
            } else {
                // Other cases like null child: no match
                return Arrays.asList(pattern);
            }
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Nut accessFor(final String realPath, final NutType type, final ProcessContext processContext)
            throws IOException {
        init();

        final Path p = baseDirectory.getChild(realPath);

        if (!(p instanceof FilePath)) {
            WuicException.throwBadArgumentException(new IllegalArgumentException(String.format("%s is not a file", p)));
        }

        final FilePath fp = FilePath.class.cast(p);
        return new FilePathNut(fp, realPath, type, getVersionNumber(realPath, processContext));
    }

    /**
     * <p>
     * Gets the last update timestamp of the given {@link Path}.
     * </p>
     *
     * @param path the path
     * @return the last timestamp
     * @throws IOException if any I/O error occurs
     */
    private Long getLastUpdateTimestampFor(final Path path) throws IOException {
        return path.getLastUpdate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Long getLastUpdateTimestampFor(final String path) throws IOException {
        return getLastUpdateTimestampFor(baseDirectory.getChild(path));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s with base directory %s", getClass().getName(), baseDirectory);
    }

    /**
     * <p>
     * Initializes the {@link com.github.wuic.path.DirectoryPath} if {@code null}. Throws an {@code BadArgumentException} if
     * the given {@code String} does not represents a directory.
     * </p>
     *
     * @throws IOException if any I/O error occurs
     */
    private void init() throws IOException {
        if (baseDirectory == null) {
            baseDirectory = createBaseDirectory();
        }
    }

    /**
     * <p>
     * Resolve the give path.
     * </p>
     *
     * @param path the path to resolve
     * @return the resolved path {@link Path}, {@code null} if it does not exists
     * @throws IOException if any I/O error occurs
     */
    private Path resolve(final String path) throws IOException {
        init();
        return baseDirectory.getChild(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Input newInputStream(final String path, final ProcessContext processContext) throws IOException {
        final Path p = resolve(path);

        if (!(p instanceof FilePath)) {
            WuicException.throwBadArgumentException(new IllegalArgumentException(String.format("%s is not a file", p)));
        }

        return FilePath.class.cast(p).openStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean exists(final String path, final ProcessContext processContext) throws IOException {
        try {
            return resolve(path) != null;
        } catch (FileNotFoundException fne) {
            return Boolean.FALSE;
        }
    }

    /**
     * <p>
     * Creates the {@link DirectoryPath} associated to the {@link AbstractNutDao#basePath}.
     * </p>
     *
     * @return the {@link DirectoryPath}
     * @throws IOException if any I/O error occurs
     */
    protected abstract DirectoryPath createBaseDirectory() throws IOException;
}
