/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jruby.ir;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.ast.Node;
import org.jruby.ast.executable.AbstractScript;
import org.jruby.ast.executable.Script;
import org.jruby.ast.executable.ScriptAndCode;
import org.jruby.compiler.NotCompilableException;
import org.jruby.ir.interpreter.Interpreter;
import org.jruby.ir.operands.IRException;
import org.jruby.ir.runtime.IRBreakJump;
import org.jruby.ir.targets.JVMVisitor;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ClassDefiningClassLoader;
import org.jruby.util.ClassDefiningJRubyClassLoader;
import org.jruby.util.cli.Options;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class Compiler extends IRTranslator<ScriptAndCode, ClassDefiningClassLoader> implements org.jruby.Compiler {
    private static final Logger LOG = LoggerFactory.getLogger("Compiler");
    private final Ruby ruby;

    public Compiler(Ruby ruby) {
        this.ruby = ruby;
    }

    /**
     * Try to compile the code associated with the given Node, returning an
     * instance of the successfully-compiled Script or null if the script could
     * not be compiled.
     *
     * @param node The node to attempt to compiled
     * @return an instance of the successfully-compiled Script, or null.
     */
    public Script tryCompile(Node node) {
        return tryCompile(node, new ClassDefiningJRubyClassLoader(ruby.getJRubyClassLoader())).script();
    }

    public ScriptAndCode tryCompile(Node node, ClassDefiningClassLoader classLoader) {
        try {
            return execute(ruby, node, classLoader);
        } catch (NotCompilableException e) {
            if (Options.JIT_LOGGING.load()) {
                LOG.error("failed to compile target script " + node.getPosition().getFile() + ": " + e.getLocalizedMessage());
                if (Options.JIT_LOGGING_VERBOSE.load()) {
                    LOG.error(e);
                }
            }
            return null;
        }
    }

    @Override
    protected ScriptAndCode execute(final IRScriptBody scope, ClassDefiningClassLoader classLoader) {
        JVMVisitor visitor;
        byte[] bytecode;
        Class compiled;

        MethodHandle _compiledHandle;
        try {
            visitor = new JVMVisitor();
            bytecode = visitor.compileToBytecode(scope);
            compiled = visitor.defineFromBytecode(scope, bytecode, classLoader);

            Method compiledMethod = compiled.getMethod("RUBY$script", ThreadContext.class,
                    StaticScope.class, IRubyObject.class, IRubyObject[].class, Block.class, RubyModule.class, String.class);
            _compiledHandle = MethodHandles.publicLookup().unreflect(compiledMethod);
        } catch (NotCompilableException nce) {
            throw nce;
        } catch (Throwable t) {
            throw new NotCompilableException("failed to compile script " + scope.getName(), t);
        }

        final MethodHandle compiledHandle = _compiledHandle;

        Script script = new AbstractScript() {
            @Override
            public IRubyObject __file__(ThreadContext context, IRubyObject self, IRubyObject[] args, Block block) {
                try {
                    return (IRubyObject) compiledHandle.invokeWithArguments(context, scope.getStaticScope(), self, IRubyObject.NULL_ARRAY, block, self.getMetaClass(), Interpreter.ROOT);
                } catch (Throwable t) {
                    Helpers.throwException(t);
                    return null; // not reached
                }
            }

            @Override
            public IRubyObject load(ThreadContext context, IRubyObject self, boolean wrap) {
                // Compiler does not support BEGIN/END yet and should fail to compile above
                {
//                BeginEndInterpreterContext ic = (BeginEndInterpreterContext) irScope.prepareForInterpretation();

                    // We get the live object ball rolling here.
                    // This give a valid value for the top of this lexical tree.
                    // All new scopes can then retrieve and set based on lexical parent.
//                StaticScope scope = ic.getStaticScope();
                }
                // Copied from Interpreter
                StaticScope sscope = scope.getStaticScope();
                RubyModule currModule = sscope.getModule();
                if (currModule == null) {
                    // SSS FIXME: Looks like this has to do with Kernel#load
                    // and the wrap parameter. Figure it out and document it here.
                    currModule = context.getRuntime().getObject();
                }

                sscope.setModule(currModule);
                DynamicScope tlbScope = scope.getToplevelScope();
                if (tlbScope == null) {
                    context.preMethodScopeOnly(sscope);
                } else {
                    sscope = tlbScope.getStaticScope();
                    context.preScopedBody(tlbScope);
                    tlbScope.growIfNeeded();
                }
                context.setCurrentVisibility(Visibility.PRIVATE);

                try {
//                    runBeginEndBlocks(ic.getBeginBlocks(), context, self, scope, null);
                    return (IRubyObject) compiledHandle.invokeWithArguments(context, sscope, self, IRubyObject.NULL_ARRAY, Block.NULL_BLOCK, currModule, Interpreter.ROOT);
                } catch (IRBreakJump bj) {
                    throw IRException.BREAK_LocalJumpError.getException(context.runtime);
                } catch (Throwable t) {
                    Helpers.throwException(t);
                    return null; // not reached
                } finally {
//                    runEndBlocks(ic.getEndBlocks(), context, self, scope, null);
                    context.popScope();
                }
            }
        };

        return new ScriptAndCode(bytecode, script);

    }

}
