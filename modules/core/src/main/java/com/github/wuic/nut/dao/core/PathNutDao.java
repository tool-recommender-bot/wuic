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


package com.github.wuic.nut.dao.core;

import com.github.wuic.NutType;
import com.github.wuic.exception.SaveOperationNotSupportedException;
import com.github.wuic.exception.wrapper.BadArgumentException;
import com.github.wuic.exception.wrapper.StreamException;
import com.github.wuic.nut.AbstractNutDao;
import com.github.wuic.nut.FilePathNut;
import com.github.wuic.nut.Nut;
import com.github.wuic.path.DirectoryPath;
import com.github.wuic.path.FilePath;
import com.github.wuic.path.Path;
import com.github.wuic.util.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
 * @version 1.1
 * @since 0.4.2
 */
public abstract class PathNutDao extends AbstractNutDao {

    /**
     * Base directory where the protocol has to look up.
     */
    private DirectoryPath baseDirectory;

    /**
     * {@code true} if the path is a regex, {@code false} otherwise
     */
    private Boolean regularExpression;

    /**
     * <p>
     * Builds a new instance with a base directory.
     * </p>
     *
     * @param base the directory where we have to look up
     * @param basePathAsSysProp {@code true} if the base path is a system property
     * @param pollingSeconds the interval for polling operations in seconds (-1 to deactivate)
     * @param proxies the proxies URIs in front of the nut
     * @param regex if the path should be considered as a regex or not
     * @param contentBasedVersionNumber  {@code true} if version number is computed from nut content, {@code false} if based on timestamp
     */
    public PathNutDao(final String base,
                      final Boolean basePathAsSysProp,
                      final String[] proxies,
                      final int pollingSeconds,
                      final Boolean regex,
                      final Boolean contentBasedVersionNumber) {
        super(base, basePathAsSysProp, proxies, pollingSeconds, contentBasedVersionNumber);
        regularExpression = regex;
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
    public List<String> listNutsPaths(final String pattern) throws StreamException {
        init();
        final Pattern compiled = Pattern.compile(regularExpression ? pattern : Pattern.quote(pattern));
        return IOUtils.listFile(DirectoryPath.class.cast(baseDirectory), compiled, skipStartsWith());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Nut accessFor(final String realPath, final NutType type) throws StreamException {
        init();

        try {
            final Path p = baseDirectory.getChild(realPath);

            if (p instanceof FilePath) {
                final FilePath fp = FilePath.class.cast(p);
                return new FilePathNut(fp, realPath, type, getVersionNumber(realPath));
            } else {
                throw new BadArgumentException(new IllegalArgumentException(String.format("%s is not a file", p)));
            }
        } catch (IOException ioe) {
            throw new StreamException(ioe);
        }
    }

    /**
     * <p>
     * Gets the last update timestamp of the given {@link Path}.
     * </p>
     *
     * @param path the path
     * @return the last timestamp
     * @throws StreamException if any I/O error occurs
     */
    private Long getLastUpdateTimestampFor(final Path path) throws StreamException {
        try {
            return path.getLastUpdate();
        } catch (IOException ioe) {
            throw new StreamException(ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Long getLastUpdateTimestampFor(final String path) throws StreamException {
        try {
            return getLastUpdateTimestampFor(baseDirectory.getChild(path));
        } catch (IOException ioe) {
            throw new StreamException(ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(final Nut nut) {
        // TODO : update path API
        throw new SaveOperationNotSupportedException(this.getClass());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean saveSupported() {
        // TODO : return true once path API supports write operations
        return false;
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
     * @throws com.github.wuic.exception.wrapper.StreamException if any I/O error occurs
     */
    private void init() throws StreamException {
        if (baseDirectory == null) {
            try {
                baseDirectory = createBaseDirectory();
            } catch (IOException ioe) {
                throw new StreamException(ioe);
            }
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
     * @throws StreamException if any I/O error occurs
     */
    private Path resolve(final String path) throws IOException, StreamException {
        init();
        return baseDirectory.getChild(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream newInputStream(final String path) throws StreamException {
        try {
            final Path p = resolve(path);

            if (p instanceof FilePath) {
                return FilePath.class.cast(p).openStream();
            } else {
                throw new BadArgumentException(new IllegalArgumentException(String.format("%s is not a file", p)));
            }
        } catch (IOException ioe) {
            throw new StreamException(ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean exists(final String path) throws StreamException {
        try {
            return resolve(path) != null;
        } catch (FileNotFoundException fne) {
            return Boolean.FALSE;
        } catch (IOException ioe) {
            throw new StreamException(ioe);
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
