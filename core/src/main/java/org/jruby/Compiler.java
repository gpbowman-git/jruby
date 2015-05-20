package org.jruby;

import org.jruby.ast.Node;
import org.jruby.ast.executable.Script;
import org.jruby.ast.executable.ScriptAndCode;
import org.jruby.util.ClassDefiningClassLoader;

/**
 * Created by uwe on 20/5/15.
 */
public interface Compiler {
    Script tryCompile(Node scriptNode);

    ScriptAndCode tryCompile(Node scriptNode, ClassDefiningClassLoader classDefiningClassLoader);
}
