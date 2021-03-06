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


package com.github.wuic.test.engine;

import com.github.wuic.EnumNutType;
import com.github.wuic.NutTypeFactory;
import com.github.wuic.engine.EngineRequest;
import com.github.wuic.engine.EngineRequestBuilder;
import com.github.wuic.engine.core.GzipEngine;
import com.github.wuic.nut.InMemoryNut;
import com.github.wuic.nut.ConvertibleNut;
import com.github.wuic.nut.Nut;
import com.github.wuic.nut.NutsHeap;
import com.github.wuic.util.InMemoryOutput;
import com.github.wuic.util.NutUtils;
import com.github.wuic.util.Pipe;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Test for GZIP support.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.5.0
 */
@RunWith(JUnit4.class)
public class GzipEngineTest {

    /**
     * Timeout.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /**
     * Tests when GZIP is enabled.
     *
     * @throws Exception if test fails
     */
    @Test
    public void enableGzipTest() throws Exception {
        final GzipEngine gzipEngine = new GzipEngine();
        gzipEngine.init(true);
        gzipEngine.setNutTypeFactory(new NutTypeFactory("UTF-8"));

        final Nut nut = new InMemoryNut("var foo = 1;".getBytes(), "foo.js", new NutTypeFactory(Charset.defaultCharset().displayName()).getNutType(EnumNutType.JAVASCRIPT), 1L, false);
        final NutsHeap heap = Mockito.mock(NutsHeap.class);
        Mockito.when(heap.getNuts()).thenReturn(Arrays.asList(nut));

        final EngineRequest request = new EngineRequestBuilder("workflow", heap, null, new NutTypeFactory(Charset.defaultCharset().displayName())).build();
        final List<ConvertibleNut> res = gzipEngine.parse(request);
        Assert.assertEquals(1, res.size());
        final InMemoryOutput bos = new InMemoryOutput(Charset.defaultCharset().displayName());
        res.get(0).transform(new Pipe.DefaultOnReady(bos));
        final PushbackInputStream pb = new PushbackInputStream(new ByteArrayInputStream(bos.execution().getByteResult()), 2 );
        final byte[] signature = new byte[2];
        pb.read(signature);
        pb.unread(signature);

        // Check magic number
        Assert.assertEquals(signature[0], (byte) 0x1f);
        Assert.assertEquals(signature[1], (byte) 0x8b);
    }

    /**
     * Disables GZIP.
     *
     * @throws Exception if test fails
     */
    @Test
    public void disableGzipTest() throws Exception {
        final GzipEngine gzipEngine = new GzipEngine();
        gzipEngine.init(false);
        gzipEngine.setNutTypeFactory(new NutTypeFactory("UTF-8"));

        final Nut nut = new InMemoryNut("var foo = 1;".getBytes(), "foo.js", new NutTypeFactory(Charset.defaultCharset().displayName()).getNutType(EnumNutType.JAVASCRIPT), 1L, false);
        final NutsHeap heap = Mockito.mock(NutsHeap.class);
        Mockito.when(heap.getNuts()).thenReturn(Arrays.asList(nut));

        final EngineRequest request = new EngineRequestBuilder("workflow", heap, null, new NutTypeFactory(Charset.defaultCharset().displayName())).build();
        final List<ConvertibleNut> res = gzipEngine.parse(request);
        Assert.assertEquals("var foo = 1;", NutUtils.readTransform(res.get(0)));
    }
}
