/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.spark;

import com.gigaspaces.spark.utils.StringCompiler;
import org.apache.zeppelin.interpreter.*;
import scala.collection.Iterator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

/**
 * Allows to append custom code to spark job. Uses %dep interpreter, so must be called before %spark is initialized.
 * Compiles code written by user, packs it into .jar and adds the jar to %dep interpreter.
 *
 * @author Leonid_Poliakov
 */
public class CompilingInterpreter extends Interpreter {
    static {
        Interpreter.register(
                "define",
                "spark",
                CompilingInterpreter.class.getName(),
                new InterpreterPropertyBuilder().build());
    }

    private DepInterpreter depInterpreter;

    public CompilingInterpreter(Properties property) {
        super(property);
    }

    @Override
    public void open() {
        depInterpreter = getDepInterpreter();
    }

    @Override
    public void close() {
    }

    @Override
    public InterpreterResult interpret(String code, InterpreterContext context) {
        File outputFolder = null;
        try {
            outputFolder = Files.createTempDirectory("jars").toFile();
        } catch (IOException up) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, "Cannot create temporary dir for compiled files");
        }

        StringCompiler compiler = new StringCompiler(outputFolder, StringCompiler.currentClassPath());
        boolean success = compiler.compile(code);
        if (!success) {
            Iterator<String> iterator = compiler.getAndRemoveMessages().iterator();
            StringBuilder builder = new StringBuilder("Compilation failure");
            while (iterator.hasNext()) {
                builder.append("\n").append(iterator.next());
            }
            return new InterpreterResult(InterpreterResult.Code.ERROR, builder.toString());
        }

        File jar = compiler.packJar();
        return depInterpreter.interpret("z.load(\"" + jar.getAbsolutePath() + "\")", context);
    }

    @Override
    public void cancel(InterpreterContext context) {
    }

    @Override
    public FormType getFormType() {
        return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) {
        return 0;
    }

    @Override
    public List<String> completion(String buf, int cursor) {
        return depInterpreter.completion(buf, cursor);
    }

    private DepInterpreter getDepInterpreter() {
        InterpreterGroup intpGroup = getInterpreterGroup();
        LazyOpenInterpreter lazy = null;
        DepInterpreter dep = null;
        synchronized (intpGroup) {
            for (Interpreter intp : getInterpreterGroup()) {
                if (intp.getClassName().equals(DepInterpreter.class.getName())) {
                    Interpreter interpreter = intp;
                    while (interpreter instanceof WrappedInterpreter) {
                        if (interpreter instanceof LazyOpenInterpreter) {
                            lazy = (LazyOpenInterpreter) interpreter;
                        }
                        interpreter = ((WrappedInterpreter) interpreter).getInnerInterpreter();
                    }
                    dep = (DepInterpreter) interpreter;
                }
            }
        }
        if (lazy != null) {
            lazy.open();
        }
        return dep;
    }
}
