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


package com.github.wuic.util;

import com.github.wuic.ApplicationConfig;
import com.github.wuic.path.DirectoryPath;
import com.github.wuic.path.DirectoryPathFactory;
import com.github.wuic.path.Path;
import com.github.wuic.path.core.FsDirectoryPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * <p>
 * Utility class built on top of the {@code java.io} package helping WUIC to deal with
 * I/O.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.3.1
 */
public final class IOUtils {

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(IOUtils.class);

    /**
     * The slash character is the standard separator used internally, even on windows platform.
     */
    public static final String STD_SEPARATOR = "/";

    /**
     * Length of a memory buffer used in WUIC.
     */
    public static final int WUIC_BUFFER_LEN = 2048;

    /**
     * All ZIP files begins with this magic number.
     */
    public static final int ZIP_MAGIC_NUMBER = 0x504b0304;

    /**
     * The line delimiter used by WUIC when generating files. \n is applied if {@link ApplicationConfig#USE_SYSTEM_LINE_SEPARATOR}
     * is not set as system property. If the property exists, then the "line.separator" property will be used.
     */
    public static final String NEW_LINE = System.getProperty("wuic.useSystemLineSeparator") != null ? System.getProperty("line.separator") : "\n";

    /**
     * A boolean indicating if a warning message has been already logged to notify a charset incorrectly configured.
     */
    private static boolean alreadyWarnCharset = false;

    /**
     * <p>
     * Prevent instantiation of this class which provides only static methods.
     * </p>
     */
    private IOUtils() {

    }

    /**
     * <p>
     * Checks that the charset is correct. If the given {@code String} is empty, then the default platform charset is used.
     * </p>
     *
     * @param charset the charset to test
     * @return the given charset if not empty, default platform charset otherwise
     */
    public static String checkCharset(final String charset) {
        if (charset.isEmpty()) {
            final String retval = Charset.defaultCharset().name();

            if (!alreadyWarnCharset) {
                alreadyWarnCharset = true;
                LOG.warn("Charset is not defined, did you configured {} property? Applying platform charset '{}'.",
                        ApplicationConfig.CHARSET,
                        retval);
            }

            return retval;
        } else {
            return charset;
        }
    }

    /**
     * <p>
     * Converts the given char array to a byte array using the {@code Charset} specified in parameter.
     * </p>
     *
     * @param chars the chars to encode
     * @return the encoded bytes
     */
    public static byte[] toBytes(final Charset charset, final char ... chars) {
        final CharBuffer cbuf = CharBuffer.wrap(chars);
        final ByteBuffer bbuf = charset.encode(cbuf);
        final byte[] b = new byte[bbuf.remaining()];
        bbuf.get(b);
        return b;
    }

    /**
     * <p>
     * Converts the given byte array to a char array using the {@code Charset} specified in parameter.
     * </p>
     *
     * @param bytes the chars to decode
     * @return the decoded chars
     */
    public static char[] toChars(final Charset charset, final byte ... bytes) {
        final ByteBuffer bbuf = ByteBuffer.wrap(bytes);
        final CharBuffer cbuf = charset.decode(bbuf);
        final char[] c = new char[cbuf.remaining()];
        cbuf.get(c);
        return c;
    }

    /**
     * <p>
     * Returns a new {@link MessageDigest} based on CRC algorithm.
     * </p>
     *
     * @return the message digest
     */
    public static MessageDigest newMessageDigest() {
        return new CrcMessageDigest();
    }

    /**
     * <p>
     * Digests each {@code String} in the given array and return the corresponding signature.
     * </p>
     *
     * @param strings the string array
     * @return the digested bytes
     */
    public static byte[] digest(final String ... strings) {
        final MessageDigest md = newMessageDigest();

        for (final String string : strings) {
            md.update(string.getBytes());
        }

        return md.digest();
    }

    /**
     * <p>
     * Digests each {@code byte} array in the given array and return the corresponding signature.
     * </p>
     *
     * @param bytes the byte arrays
     * @return the digested bytes
     */
    public static byte[] digest(final byte[] ... bytes) {
        final MessageDigest md = newMessageDigest();

        for (final byte[] byteArray : bytes) {
            md.update(byteArray);
        }

        return md.digest();
    }

    /**
     * <p>
     * Merges the given {@code String} array with the standard {@link IOUtils#STD_SEPARATOR separator}.
     * </p>
     *
     * @param paths the paths to be merged
     * @return the merged paths
     */
    public static String mergePath(final String ... paths) {
        return StringUtils.merge(paths, STD_SEPARATOR);
    }

    /**
     * <p>
     * Makes sure a given path uses the slash character has path separator by replacing all backslashes.
     * </p>
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    public static String normalizePathSeparator(final String path) {
        return path.replace("\\", STD_SEPARATOR);
    }

    /**
     * <p>
     * Tries to close the given objects and log the {@link IOException} at INFO level
     * to make the code more readable when we assume that the {@link IOException} won't be managed.
     * </p>
     *
     * <p>
     * Also ignore {@code null} parameters.
     * </p>
     *
     * @param closeableArray the objects to close
     */
    public static void close(final Closeable... closeableArray) {
        for (Closeable closeable : closeableArray) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (IOException ioe) {
                LOG.info("Can't close the object", ioe);
            }
        }
    }

    /**
     * <p>
     * Reads the given reader and returns its content in a {@code String}.
     * </p>
     *
     * @param reader the reader
     * @return the content
     * @throws IOException if an I/O error occurs
     */
    public static String readString(final Reader reader) throws IOException {
        final StringBuilder builder = new StringBuilder();
        read(reader, builder);
        return builder.toString();
    }

    /**
     * <p>
     * Reads the given reader and append all chars to the specified {@link StringBuilder}.
     * </p>
     *
     * @param reader the reader
     * @param builder the string builder
     * @throws IOException if an I/O error occurs
     */
    public static void read(final Reader reader, final StringBuilder builder) throws IOException {
        final char[] buff = new char[IOUtils.WUIC_BUFFER_LEN];
        int offset;

        // read content
        while ((offset = reader.read(buff)) != -1) {
            builder.append(buff, 0, offset);
        }
    }

    /**
     * <p>
     * Copies the data from the given input stream into the given output stream and doesn't wrap any {@code IOException}.
     * </p>
     *
     * @param is the {@code InputStream}
     * @param os the {@code OutputStream}
     * @return the content length
     * @throws IOException in an I/O error occurs
     */
    public static int copyStream(final InputStream is, final OutputStream os)
            throws IOException {
        int retval = 0;
        int offset;
        final byte[] buffer = new byte[WUIC_BUFFER_LEN];

        while ((offset = is.read(buffer)) != -1) {
            os.write(buffer, 0, offset);
            retval += offset - 1;
        }

        return retval;
    }

    /**
     * <p>
     * Copies the stream read from the input to the given output.
     * If the input is a text, data will be copied to the output's writer, otherwise to the output's stream.
     * </p>
     *
     * @param input the input
     * @param output the output
     * @return the opened {@code Writer} in case of text, or the opened {@code OutputStream} otherwise
     * @throws IOException if copy fails
     */
    public static Object copyStream(final Input input, final Output output) throws IOException {
        final Pipe.Execution execution = input.execution();

        if (execution.isText()) {
            final Writer writer = output.writer();
            execution.writeResultTo(writer);
            return writer;
        }  else {
            final OutputStream outputStream = output.outputStream();
            execution.writeResultTo(outputStream);
            return outputStream;
        }
    }

    /**
     * <p>
     * Copies the data from the given reader into the given writer and doesn't wrap any {@code IOException}.
     * </p>
     *
     * @param reader the {@code Reader}
     * @param writer the {@code Writer}
     * @return the content length
     * @throws IOException in an I/O error occurs
     */
    public static int copyStream(final Reader reader, final Writer writer)
            throws IOException {
        int retval = 0;
        int offset;
        final char[] buffer = new char[WUIC_BUFFER_LEN];

        while ((offset = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, offset);
            retval += offset - 1;
        }

        return retval;
    }

    /**
     * <p>
     * Checks if the path path points to a a valid ZIP archive.
     * </p>
     *
     * @param file the path to check
     * @return {@code true} if path is an archive, {@code false} otherwise
     */
    public static Boolean isArchive(final File file) throws IOException {
        // File must exist, reachable and with a sufficient size to contain magic number
        if (file == null || !file.isFile() || !file.canRead() || file.length() < NumberUtils.TWO * NumberUtils.TWO) {
            return Boolean.FALSE;
        } else {
            return isArchive(new BufferedInputStream(new FileInputStream(file)));
        }
    }

    /**
     * <p>
     * Checks if the given stream points represents a valid ZIP archive.
     * </p>
     *
     * @param inputStream the stream to check
     * @return {@code true} if the stream should be an archive, {@code false} otherwise
     */
    public static Boolean isArchive(final InputStream inputStream) throws IOException {
        DataInputStream in = null;

        try {
            // Check that the path begins with magic number
            in = new DataInputStream(inputStream);
            return in.readInt() == ZIP_MAGIC_NUMBER;
        } catch (EOFException oef) {
            LOG.trace("File is not an archive, probably empty file", oef);
            return Boolean.FALSE;
        } finally {
            close(in);
        }
    }

    /**
     * <p>
     * Lists all the files from the given directory matching the given pattern.
     * </p>
     *
     * <p>
     * For instance, if a directory /foo contains a path in foo/oof/path.js, calling this method with an {@link Pattern}
     * .* will result in an array containing the {@code String} {@code oof/path.js}.
     * </p>
     *
     * @param parent the directory
     * @param pattern the pattern to filter files
     * @return the matching files
     * @throws IOException if any I/O error occurs
     */
    public static List<String> listFile(final DirectoryPath parent, final Pattern pattern) throws IOException {
        return listFile(parent, "", pattern, Collections.<String>emptyList());
    }

    /**
     * <p>
     * Searches as specified in {@link #listFile(com.github.wuic.path.DirectoryPath, java.util.regex.Pattern)} with
     * a list that contains all begin paths to ignore.
     * </p>
     *
     * @param parent the directory
     * @param pattern the pattern to filter files
     * @param skipStartsWithList a list that contains all begin paths to ignore
     * @return the matching files
     * @throws IOException if any I/O error occurs
     */
    public static List<String> listFile(final DirectoryPath parent, final Pattern pattern, final List<String> skipStartsWithList)
            throws IOException {
        return listFile(parent, "", pattern, skipStartsWithList);
    }

    /**
     * <p>
     * Lists the files ending with the given {@code Sting} in the directory path..
     * </p>
     *
     * @param parent the parent
     * @param relativePath the directory path relative to the parent
     * @param endWith the directory path relative to the parent
     * @param skipStartsWithList a list that contains all begin paths to ignore
     * @return the matching files
     * @throws IOException if any I/O error occurs
     */
    public static List<String> listFile(final DirectoryPath parent, final String relativePath, final String endWith, final List<String> skipStartsWithList)
            throws IOException {
        final String[] children = parent.list();
        final List<String> retval = new ArrayList<String>();

        // Check each child path
        childrenLoop:
        for (final String child : children) {
            final Path path = parent.getChild(child);
            final String childRelativePath = relativePath.isEmpty() ? child : mergePath(relativePath, child);

            // Child is a directory, search recursively
            if (path instanceof DirectoryPath) {

                // Search recursively if and only if the beginning of the path if not in the excluding list
                for (final String skipStartWith : skipStartsWithList) {
                    if (childRelativePath.startsWith(skipStartWith)) {
                        continue childrenLoop;
                    }
                }

                retval.addAll(listFile(DirectoryPath.class.cast(path), childRelativePath, endWith, skipStartsWithList));
                // Files matches, return
            } else if (childRelativePath.endsWith(endWith)) {
                retval.add(childRelativePath);
            }
        }

        return retval;
    }

    /**
     * <p>
     * Lists the files matching the given pattern in the directory path and its subdirectory represented by
     * a specified {@code relativePath}.
     * </p>
     *
     * @param parent the parent
     * @param relativePath the directory path relative to the parent
     * @param pattern the pattern which filters files
     * @param skipStartsWithList a list that contains all begin paths to ignore
     * @return the matching files
     * @throws IOException if any I/O error occurs
     */
    public static List<String> listFile(final DirectoryPath parent, final String relativePath, final Pattern pattern, final List<String> skipStartsWithList)
            throws IOException {
        final String[] children = parent.list();
        final List<String> retval = new ArrayList<String>();

        // Check each child path
        childrenLoop:
        for (final String child : children) {
            final Path path = parent.getChild(child);
            final String childRelativePath = relativePath.isEmpty() ? child : mergePath(relativePath, child);

            // Child is a directory, search recursively
            if (path instanceof DirectoryPath) {

                // Search recursively if and only if the beginning of the path if not in the excluding list
                for (final String skipStartWith : skipStartsWithList) {
                    if (childRelativePath.startsWith(skipStartWith)) {
                        continue childrenLoop;
                    }
                }

                retval.addAll(listFile(DirectoryPath.class.cast(path), childRelativePath, pattern, skipStartsWithList));
            // Files matches, return
            } else if (pattern.matcher(childRelativePath).matches()) {
                retval.add(childRelativePath);
            }
        }

        return retval;
    }

    /**
     * <p>
     * Returns a hierarchy of {@link Path paths} represented by the given {@code String}. The given {@link DirectoryPathFactory}
     * is used to create directories.
     * </p>
     *
     * @param path the path hierarchy
     * @param factory the factory.
     * @return the last {@link Path} of the hierarchy with its parent
     * @throws IOException if any I/O error occurs
     */
    public static Path buildPath(final String path, final DirectoryPathFactory factory) throws IOException {
        LOG.debug("Build path for '{}'", path);

        // Always use '/' separator, even on windows
        final String absolutePath = IOUtils.normalizePathSeparator(path);
        final String[] tree = absolutePath.split(IOUtils.STD_SEPARATOR);

        // Build the root => force the path to / if its empty
        final String root = tree.length == 0 ? "/" : tree[0];
        final DirectoryPath retval =
                factory.create(root.isEmpty() && path.startsWith(IOUtils.STD_SEPARATOR) ? IOUtils.STD_SEPARATOR : root);

        // Build child path
        if (tree.length > 1) {
            return retval.getChild(IOUtils.mergePath(Arrays.copyOfRange(tree, 1, tree.length)));
        // No parent
        } else {
            return retval;
        }
    }

    /**
     * <p>
     * Returns a hierarchy of {@link Path paths} represented by the given {@code String}.
     * Uses a {@link FsDirectoryPathFactory} to create instances.
     * </p>
     *
     * @param charset the charset
     * @param path the path hierarchy
     * @param manager the temporary file manager
     * @return the last {@link Path} of the hierarchy with its parent
     * @throws IOException if any I/O error occurs
     */
    public static Path buildPath(final String path, final String charset, final TemporaryFileManager manager) throws IOException {
        return buildPath(path, new FsDirectoryPathFactory(charset, manager));
    }

    /**
     * <p>
     * Deletes the {@link File} quietly. The methods checks if the file is not {@code null} and calls itself recursively
     * if the file is a directory.
     * </p>
     *
     * @param file the file or directory to delete
     */
    public static void delete(final File file) {
        if (file != null) {
            if (!file.isFile() && file.exists()) {
                for (final File f : file.listFiles()) {
                    delete(f);
                }
            }

            file.delete();
        }
    }

    /**
     * <p>
     * A {@link CRC32} is wrapped inside this class which is a {@link MessageDigest}.
     * </p>
     *
     * @author Guillaume DROUET
          * @since 0.5.0
     */
    public static final class CrcMessageDigest extends MessageDigest {

        /**
         * The CRC32 instance.
         */
        private final CRC32 crc;

        /**
         * Builds a new instance.
         */
        public CrcMessageDigest() {
            super("");
            crc = new CRC32();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void engineUpdate(final byte input) {
            crc.update(input);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void engineUpdate(final byte[] input, final int offset, final int len) {
            crc.update(input, offset, len);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected byte[] engineDigest() {
            ByteBuffer buffer = ByteBuffer.allocate(NumberUtils.HEIGHT);
            buffer.putLong(crc.getValue());
            return buffer.array();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void engineReset() {
            crc.reset();
        }
    }
}
