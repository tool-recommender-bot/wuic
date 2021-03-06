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


package com.github.wuic.nut.filter.setter;

import com.github.wuic.ApplicationConfig;
import com.github.wuic.config.PropertySetter;
import com.github.wuic.exception.WuicException;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 * Setter for the {@link com.github.wuic.ApplicationConfig#REGEX_EXPRESSIONS} property.
 * </p>
 *
 * <p>
 * Uris array is specified as a {@code String} containing each value separated by a '\n' character.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.4.5
 */
public class RegexExpressionsPropertySetter extends PropertySetter<String[]> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void set(final Object value) {
        if (value == null) {
            put(getPropertyKey(), value);
        } else if (value instanceof String) {
            final String[] split = value.toString().split("\n");
            final Set<String> trimmed = new HashSet<String>();

            for (final String regex : split) {
                trimmed.add(regex.trim());
            }

            put(getPropertyKey(), trimmed.toArray(new String[trimmed.size()]));
        } else {
            WuicException.throwBadArgumentException(new IllegalArgumentException(
                    String.format("Value '%s' associated to key '%s' must be a String", value, getPropertyKey())));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyKey() {
        return ApplicationConfig.REGEX_EXPRESSIONS;
    }
}
