/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The Java Pathfinder core (jpf-core) platform is licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.nasa.jpf.listener;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMInvokeInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

import java.io.PrintWriter;

/**
 * Small listener that skips the body of a given method.
 */

class MethodSkipper extends ListenerAdapter {
    private boolean skipInit = false;
    private String methodToSkip;

    private MethodInfo lastMi;
    private PrintWriter out;

    private boolean skip;
    private MethodInfo miMain; // just to make init skipping more efficient

    public MethodSkipper(Config config, JPF jpf) {
        /** @jpfoption et.skip_init : boolean - do not log execution before entering main() (default=true). */
        skipInit = config.getBoolean("et.skip_init", true);

        if (skipInit) {
            skip = true;
        }
        methodToSkip = "write";
        out = new PrintWriter(System.out, true);
    }


    @Override
    public void instructionExecuted(VM vm, ThreadInfo thread, Instruction nextInsn, Instruction executedInsn) {
        MethodInfo mi = executedInsn.getMethodInfo();

        if (skip) {
            if (mi == miMain) {
                skip = false;
            } else {
                return;
            }
        }

        if (executedInsn instanceof JVMInvokeInstruction) {
            JVMInvokeInstruction call = (JVMInvokeInstruction) executedInsn;
            mi = call.getInvokedMethod(thread);

            if (thread.getNextPC() == call)
                return;

            if (mi.getName().equals(methodToSkip)) {
                Instruction nextInstruction = thread.getPC().getNext();
                while (!(nextInstruction instanceof JVMReturnInstruction)) {
                    nextInstruction = nextInstruction.getNext();
                }
                thread.skipInstruction(nextInstruction);
            }
        }
    }

    /*
     * those are not really required, but mark the transition boundaries
     */
    @Override
    public void stateRestored(Search search) {
        int id = search.getStateId();
        out.println("----------------------------------- [" +
                search.getDepth() + "] restored: " + id);
    }

    //--- the ones we are interested in
    @Override
    public void searchStarted(Search search) {
        out.println("----------------------------------- search started");
        if (skipInit) {
            ThreadInfo tiCurrent = ThreadInfo.getCurrentThread();
            miMain = tiCurrent.getEntryMethod();

            out.println("      [skipping static init instructions]");
        }
    }

    @Override
    public void stateAdvanced(Search search) {
        int id = search.getStateId();

        out.print("----------------------------------- [" +
                search.getDepth() + "] forward: " + id);
        if (search.isNewState()) {
            out.print(" new");
        } else {
            out.print(" visited");
        }

        if (search.isEndState()) {
            out.print(" end");
        }

        out.println();
        lastMi = null;
    }

    @Override
    public void stateBacktracked(Search search) {
        int id = search.getStateId();
        lastMi = null;
        out.println("----------------------------------- [" +
                search.getDepth() + "] backtrack: " + id);
    }

    @Override
    public void searchFinished(Search search) {
        out.println("----------------------------------- search finished");

    }

}
