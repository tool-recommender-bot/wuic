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

import com.github.wuic.ApplicationConfig;
import com.github.wuic.EnumNutType;
import com.github.wuic.NutType;
import com.github.wuic.config.Alias;
import com.github.wuic.config.BooleanConfigParam;
import com.github.wuic.config.Config;
import com.github.wuic.config.StringConfigParam;
import com.github.wuic.engine.EngineService;
import com.github.wuic.engine.EngineType;
import com.github.wuic.exception.WuicException;
import com.github.wuic.util.NumberUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>
 * This engine parses JS files thanks to a {@link SourceMapLineInspector}.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.4.5
 */
@EngineService(injectDefaultToWorkflow = true, isCoreEngine = true)
@Alias("javascriptInspector")
public class JavascriptInspectorEngine extends TextInspectorEngine {

    /**
     * <p>
     * Initializes a new instance.
     * </p>
     *
     * @param inspect activate inspection or not
     */
    @Config
    public void init(
            @BooleanConfigParam(defaultValue = true, propertyKey = ApplicationConfig.INSPECT) final Boolean inspect,
            @StringConfigParam(defaultValue = "", propertyKey = ApplicationConfig.WRAP_PATTERN) final String wrapPattern) {
        init(inspect);
        addInspector(new SourceMapLineInspector(this));

        if ("".equals(wrapPattern)) {
            addInspector(new AngularTemplateInspector(null));
        } else {
            final int paramIndex = wrapPattern.indexOf('%');

            if (paramIndex == -1) {
                WuicException.throwBadArgumentException(
                        new IllegalArgumentException(
                                "Wrap pattern must contains a String.format() parameter:" + wrapPattern));
            }

            addInspector(new AngularTemplateInspector(Pattern.quote(wrapPattern.substring(0, paramIndex))
                    + '%'
                    + wrapPattern.charAt(paramIndex + 1)
                    + Pattern.quote(wrapPattern.substring(paramIndex + NumberUtils.TWO))));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NutType> getNutTypes() {
        return Arrays.asList(getNutTypeFactory().getNutType(EnumNutType.JAVASCRIPT));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EngineType getEngineType() {
        return EngineType.INSPECTOR;
    }
}
