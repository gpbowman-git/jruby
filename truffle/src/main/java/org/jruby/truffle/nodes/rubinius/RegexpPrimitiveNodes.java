/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.rubinius;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.joni.Matcher;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.core.*;
import org.jruby.util.StringSupport;

/**
 * Rubinius primitives associated with the Ruby {@code Regexp} class.
 * <p>
 * Also see {@link RubyRegexp}.
 */
public abstract class RegexpPrimitiveNodes {

    @RubiniusPrimitive(name = "regexp_initialize")
    public static abstract class RegexpInitializePrimitiveNode extends RubiniusPrimitiveNode {

        public RegexpInitializePrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization
        public RubyRegexp initialize(RubyRegexp regexp, RubyString pattern, int options) {
            regexp.initialize(this, pattern.getByteList(), options);
            return regexp;
        }

    }

    @RubiniusPrimitive(name = "regexp_search_region", lowerFixnumParameters = {1, 2})
    public static abstract class RegexpSearchRegionPrimitiveNode extends RubiniusPrimitiveNode {

        public RegexpSearchRegionPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization
        public Object searchRegion(RubyRegexp regexp, RubyString string, int start, int end, boolean forward) {
            if (regexp.getRegex() == null) {
                throw new RaiseException(getContext().getCoreLibrary().typeError("uninitialized Regexp", this));
            }

            if (string.scanForCodeRange() == StringSupport.CR_BROKEN) {
                throw new RaiseException(getContext().getCoreLibrary().argumentError(
                        String.format("invalid byte sequence in %s", string.getByteList().getEncoding()), this));
            }

            final Matcher matcher = regexp.getRegex().matcher(string.getByteList().bytes());

            return regexp.matchCommon(string, false, false, matcher, start, end);
        }

    }

    @RubiniusPrimitive(name = "regexp_set_last_match")
    public static abstract class RegexpSetLastMatchPrimitiveNode extends RubiniusPrimitiveNode {

        public RegexpSetLastMatchPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization
        public Object setLastMatch(RubyClass regexpClass, Object matchData) {
            getContext().getThreadManager().getCurrentThread().getThreadLocals().getObjectType().setInstanceVariable(
                    getContext().getThreadManager().getCurrentThread().getThreadLocals(), "$~", matchData);

            return matchData;
        }

    }

    @RubiniusPrimitive(name = "regexp_set_block_last_match")
    public static abstract class RegexpSetBlockLastMatchPrimitiveNode extends RubiniusPrimitiveNode {

        public RegexpSetBlockLastMatchPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization
        public RubyBasicObject setBlockLastMatch(RubyClass regexpClass) {
            // TODO CS 7-Mar-15 what does this do?
            return nil();
        }

    }
    
}
