package com.github.wuic.test.xml;

import com.github.wuic.NutType;
import com.github.wuic.config.ConfigConstructor;
import com.github.wuic.config.StringConfigParam;
import com.github.wuic.exception.NutNotFoundException;
import com.github.wuic.exception.wrapper.StreamException;
import com.github.wuic.nut.Nut;
import com.github.wuic.nut.dao.NutDao;
import com.github.wuic.nut.dao.NutDaoListener;
import com.github.wuic.nut.dao.NutDaoService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <p>
 * Mocked DAO builder.
 * </p>
 *
 * @author Guillaume DROUET
 * @version 1.1
 * @since 0.4.0
 */
@NutDaoService
public class MockDao implements NutDao {

    /**
     * <p>
     * Builds a new instance.
     * </p>
     * 
     * @param foo custom property
     */
    @ConfigConstructor
    public MockDao(@StringConfigParam(propertyKey = "c.g.dao.foo", defaultValue = "") String foo) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void observe(final String realPath, final NutDaoListener... listeners) throws StreamException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Nut> create(final String path) throws StreamException {
        return create(path, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Nut> create(final String path, final PathFormat format) throws StreamException {
        try {
            final Nut nut = mock(Nut.class);
            when(nut.getNutType()).thenReturn(NutType.CSS);
            when(nut.getName()).thenReturn("foo.css");
            final List<Nut> nuts = new ArrayList<Nut>();
            nuts.add(nut);
            when(nut.openStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(nut.isAggregatable()).thenReturn(true);
            when(nut.getVersionNumber()).thenReturn(new BigInteger("1"));
            return nuts;
        } catch (NutNotFoundException se) {
            throw new RuntimeException(se);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String proxyUriFor(final Nut nut) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(final Nut nut) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean saveSupported() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NutDao withRootPath(final String rootPath) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream newInputStream(final String path) throws StreamException {
        return null;
    }
}