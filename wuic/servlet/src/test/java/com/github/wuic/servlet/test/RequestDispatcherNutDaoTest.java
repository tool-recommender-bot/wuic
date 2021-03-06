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


package com.github.wuic.servlet.test;

import com.github.wuic.NutTypeFactory;
import com.github.wuic.ProcessContext;
import com.github.wuic.servlet.ServletProcessContext;
import com.github.wuic.nut.dao.servlet.RequestDispatcherNutDao;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * Tests for {@link com.github.wuic.nut.dao.servlet.RequestDispatcherNutDao}.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.5.2
 */
@RunWith(JUnit4.class)
public class RequestDispatcherNutDaoTest {

    /**
     * The DAO.
     */
    private RequestDispatcherNutDao dao;

    /**
     * The request.
     */
    private final AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<HttpServletRequest>();

    /**
     * Timeout.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /**
     * <p>
     * Initializes the DAO.
     * </p>
     *
     * @throws Exception if test fails
     */
    @Before
    public void init() throws Exception {
        final RequestDispatcher requestDispatcher = Mockito.mock(RequestDispatcher.class);
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final HttpServletRequest req = HttpServletRequest.class.cast(invocationOnMock.getArguments()[0]);

                if (req.getPathInfo().contains("foo.js")) {
                    capturedRequest.set(req);
                    final PrintWriter pw = HttpServletResponse.class.cast(invocationOnMock.getArguments()[1]).getWriter();
                    pw.print("var foo;");
                    pw.flush();
                }

                return null;
            }
        }).when(requestDispatcher).include(Mockito.any(HttpServletRequest.class), Mockito.any(HttpServletResponse.class));

        final ServletContext sc = Mockito.mock(ServletContext.class);

        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                return new ByteArrayInputStream("var bar;".getBytes());
            }
        }).when(sc).getResourceAsStream("/bar.js");

        Mockito.when(sc.getRequestDispatcher(Mockito.anyString())).thenReturn(requestDispatcher);
        dao = new RequestDispatcherNutDao();
        dao.init("/", null, -1, "foo.*");
        dao.init(null, false, null);
        dao.setNutTypeFactory(new NutTypeFactory(Charset.defaultCharset().displayName()));
        dao.setServletContext(sc);
    }

    /**
     * <p>
     * Tests when inclusion is performed to check if nut exists.
     * </p>
     *
     * @throws Exception if test fails
     */
    @Test
    public void existsTest() throws Exception {
        Assert.assertTrue(dao.exists("foo.js", ProcessContext.DEFAULT));
        Assert.assertTrue(dao.exists("bar.js", ProcessContext.DEFAULT));
        Assert.assertFalse(dao.exists("foo.css", ProcessContext.DEFAULT));
        Assert.assertFalse(dao.exists("bar.css", ProcessContext.DEFAULT));
    }

    /**
     * <p>
     * Tests when {@link ServletContext#getResourceAsStream(String)} is performed.
     * </p>
     *
     * @throws Exception if test fails
     */
    @Test
    public void resourceAsStreamTest() throws Exception {
        Assert.assertEquals("var bar;", dao.create("bar.js", ProcessContext.DEFAULT).get(0).openStream().execution().toString());
    }

    /**
     * <p>
     * Tests when inclusion is performed with a custom {@link com.github.wuic.ProcessContext}.
     * </p>
     *
     * @throws Exception if test fails
     */
    @Test
    public void processContextTest() throws Exception {
        final HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        dao.create("foo.js", new ServletProcessContext(req)).get(0).openStream();

        Assert.assertNotNull(capturedRequest.get());
        Assert.assertEquals(capturedRequest.get().getClass(), HttpServletRequestWrapper.class);

        ServletRequest servletRequest = capturedRequest.get();

        while (servletRequest instanceof HttpServletRequestWrapper) {
            servletRequest = HttpServletRequestWrapper.class.cast(servletRequest).getRequest();
        }

        Assert.assertEquals(req, servletRequest);
    }
}
