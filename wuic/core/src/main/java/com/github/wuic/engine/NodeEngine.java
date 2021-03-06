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


package com.github.wuic.engine;

import com.github.wuic.Logging;
import com.github.wuic.NutType;
import com.github.wuic.NutTypeFactory;
import com.github.wuic.NutTypeFactoryHolder;
import com.github.wuic.exception.WuicException;
import com.github.wuic.nut.ConvertibleNut;
import com.github.wuic.util.BiFunction;
import com.github.wuic.util.NumberUtils;
import com.github.wuic.util.Timer;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * <p>
 * Linkable {@link Engine}.
 * </p>
 *
 * <p>
 * Fundamental design inside WUIC is to use a set of {@link NodeEngine} to be executed.
 * They are structured using the chain of responsibility design pattern. Each engine is
 * in charge of the execution of the next engine and could decide not to execute it.
 * </p>
 *
 * <p>
 * The {@link BiFunction} interface is implemented by this class in order to register it to any parsed nut via the
 * method {@link ConvertibleNut#addVersionNumberCallback(BiFunction)} which allows the subclass to transform the version
 * number.
 * </p>
 *
 * @author Guillaume DROUET
 * @since 0.4.4
 */
public abstract class NodeEngine extends Engine implements NutTypeFactoryHolder {

    /**
     * The next engine.
     */
    private NodeEngine nextEngine;

    /**
     * Previous engine.
     */
    private NodeEngine previousEngine;

    /**
     * Nut type factory.
     */
    private NutTypeFactory nutTypeFactory;

    /**
     * Callback to add to any parsed nut.
     */
    private BiFunction<ConvertibleNut, Long, Long> versionNumberCallback;

    /**
     * <p>
     * Link the given {@link NodeEngine engines}. They will be linked respecting the order of the implied by their
     * {@link NodeEngine#getEngineType()}.
     * </p>
     *
     * <p>
     * If an {@link NodeEngine} is already chained to other {@link NodeEngine engines}, any engine won't be added
     * as the next engine but to the end of the existing chain.
     * </p>
     *
     * <p>
     * If two different instances of the same class appear in the chain, then the first one will be replaced by the
     * second one, keeping the original position.
     * </p>
     *
     * @param engines the engines
     * @return the first engine of the given array
     */
    public static NodeEngine chain(final NodeEngine ... engines) {
        if (engines.length == 0) {
            WuicException.throwBadArgumentException(new IllegalArgumentException(
                    "A chain must be built with a non-empty array of engines"));
        }

        final List<NodeEngine> flatten = new LinkedList<NodeEngine>();
        final Deque<NodeEngine> retval = new LinkedList<NodeEngine>();

        // Flat the all the chains to improve data structure manipulations
        for (final NodeEngine engine : engines) {
            NodeEngine next = engine;

            if (engine == null) {
                continue;
            }

            do {
                flatten.add(next);
                next = next.nextEngine;
            } while (next != null);
        }

        Collections.sort(flatten);

        // Going to reorganize the chain to keep one instance per class
        forLoop :
        for (final NodeEngine engine : flatten) {

            // Descending iteration to keep duplicate instance on the right and not on the left
            final ListIterator<NodeEngine> it = flatten.listIterator(flatten.size());

            for (; it.hasPrevious();) {
                final NodeEngine previous = it.previous();

                // Already added in the chain, nothing to add
                if (retval.contains(previous)) {
                    break;
                    // Two instances of the same class, keep only one
                } else if (engine.getClass().equals(previous.getClass())) {
                    if (!retval.isEmpty()) {
                        retval.getLast().setNext(previous);
                    } else {
                        // This is the head of the chain
                        previous.previousEngine = null;
                    }

                    retval.add(previous);
                    continue forLoop;
                }
            }

            if (!retval.contains(engine)) {
                if (!retval.isEmpty()) {
                    retval.getLast().setNext(engine);
                }

                retval.add(engine);
            }
        }

        return retval.getFirst();
    }
    
    /**
     * <p>
     * Gets the all {@link com.github.wuic.NutType types} supported by this engine.
     * </p>
     *
     * @return the {@link com.github.wuic.NutType}
     */
    public abstract List<NutType> getNutTypes();

    /**
     * <p>
     * Sets the version number callback to this engine.
     * </p>
     *
     * @param callback the callback
     */
    public void setVersionNumberCallback(final BiFunction<ConvertibleNut, Long, Long> callback) {
        versionNumberCallback = callback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNutTypeFactory(final NutTypeFactory nutTypeFactory) {
        this.nutTypeFactory = nutTypeFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ConvertibleNut> parse(final EngineRequest request) throws WuicException {
        if (request.shouldSkip(getEngineType())) {
            // Call next engine in chain
            if (getNext() != null) {
                return getNext().parse(new EngineRequestBuilder(request).nuts(request.getNuts()).build());
            } else {
                return request.getNuts();
            }
        } else {
            final Timer timer = request.createTimer();
            timer.start();
            final List<ConvertibleNut> nuts = internalParse(request);
            registerVersionNumberCallback(nuts);
            final long elapsed = timer.end();
            Logging.TIMER.log("Parse operation by node engine executed in {}s", (float) (elapsed) / (float) NumberUtils.ONE_THOUSAND);
            request.reportParseEngine(elapsed);

            // Call next engine in chain
            if (getNext() != null && callNextEngine()) {
                return getNext().parse(new EngineRequestBuilder(request).nuts(nuts).build());
            } else {
                return nuts;
            }
        }
    }

    /**
     * <p>
     * The next {@link NodeEngine} to be execute din the chain of responsibility. If
     * it is not set, then this {@link NodeEngine} is the last one to be executed.
     * </p>
     *
     * @param next the next {@link Engine}
     */
    public void setNext(final NodeEngine next) {
        nextEngine = next;

        if (nextEngine != null) {
            nextEngine.previousEngine = this;
        }
    }

    /**
     * <p>
     * Registers this instance as a version number callback to each nut of the given list.
     * Recursive call is made on the referenced nuts.
     * </p>
     *
     * @param convertibleNuts the nuts
     */
    private void registerVersionNumberCallback(final List<ConvertibleNut> convertibleNuts) {
        if (convertibleNuts != null && versionNumberCallback != null) {
            for (final ConvertibleNut convertibleNut : convertibleNuts) {
                // Register callback only for types that belong to that chain
                if (getNutTypes().contains(convertibleNut.getNutType())) {
                    convertibleNut.addVersionNumberCallback(versionNumberCallback);
                    registerVersionNumberCallback(convertibleNut.getReferencedNuts());
                }
            }
        }
    }

    /**
     * <p>
     * Returns the next engine previously set with {@link NodeEngine#setNext(NodeEngine)}
     * method.
     * </p>
     *
     * @return the next {@link Engine}
     */
    public NodeEngine getNext() {
        return nextEngine;
    }

    /**
     * <p>
     * Returns the previous engine in the chain.
     * </p>
     *
     * @return the previous {@link Engine}
     */
    public NodeEngine getPrevious() {
        return previousEngine;
    }

    /**
     * <p>
     * Indicates if the next engine should be called by the base class or if this responsibility is delegated to the
     * subclass.
     * </p>
     *
     * @return {@code true} if {@link NodeEngine} is responsible of calling next engine, {@code false} otherwise
     */
    protected boolean callNextEngine() {
        return true;
    }

    /**
     * <p>
     * Gets the nut type factory.
     * </p>
     *
     * @return the factory
     */
    protected NutTypeFactory getNutTypeFactory() {
        return nutTypeFactory;
    }
}
