package gov.nasa.jpf;

import gov.nasa.jpf.jvm.bytecode.*;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.vm.*;
import gov.nasa.jpf.vm.bytecode.FieldInstruction;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Listener implementing a method-summary utility.
 */
public class SummaryCreator extends RecordingListener {
    // contains the names of native method calls that are known not to have
    // side-effects that can't be captured in the summary
    private static HashSet<String> nativeWhiteList = new HashSet<>();

    private static SummaryContainer container = new SummaryContainer();
    private static HashMap<String, MethodContext> contextMap = new HashMap<>();
    private static HashMap<String, MethodModifications> modificationMap = new HashMap<>();

    private boolean skipInit = false;
    private boolean skipped = false;

    private PrintWriter out;

    private boolean skip;
    private MethodInfo miMain; // just to make init skipping more efficient

    public SummaryCreator(Config config, JPF jpf) {
        //  @jpfoption et.skip_init : boolean - do not log execution before entering main() (default=true).
        skipInit = config.getBoolean("et.skip_init", true);
        if (skipInit) {
            skip = true;
        }
        reinitialise();

        out = new PrintWriter(System.out, true);
        out.println("~Summaries active~");
    }

    // necessary for tests that re-run searches
    private void reinitialise() {
        recorded = new HashSet<>();
        recording = new HashSet<>();
        blackList = new HashSet<>();
        nativeWhiteList = new HashSet<>();

        container = new SummaryContainer();
        contextMap = new HashMap<>();
        counterContainer = new CounterContainer();
        modificationMap = new HashMap<>();


        // Test gov.nasa.jpf.test.mc.basic.AttrsTest
        // This might actually be "OK",
        // if breaking attributes only affects other extensions, not core?
        blackList.add("java.lang.Integer.intValue()I");

        nativeWhiteList.add("matches");
        nativeWhiteList.add("desiredAssertionStatus");
        nativeWhiteList.add("print");
        nativeWhiteList.add("println");
        nativeWhiteList.add("min");
        nativeWhiteList.add("max");
    }

    private void blacklistAndResetRecording(String reason) {
        for (String methodName : recording) {
            assert (!recorded.contains(methodName));
            counterContainer.countInterruptedRecording(methodName, reason);
            blackList.add(methodName);
        }

        recording = new HashSet<>();
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo ti, Instruction instructionToExecute) {
        MethodInfo mi = instructionToExecute.getMethodInfo();

        if (skip || mi == null) {
            return;
        }

        if (instructionToExecute instanceof JVMInvokeInstruction) {
            JVMInvokeInstruction call = (JVMInvokeInstruction) instructionToExecute;

            mi = call.getInvokedMethod();
            if (mi == null) {
                return;
            }
            String methodName = mi.getFullName();

            if (container.hasSummariesForMethod(methodName)) {
                counterContainer.countAttemptedSummaryMatch(methodName);

                MethodSummary summary = getApplicableSummary(vm, ti, mi, call);
                if (summary == null) {
                    return;
                }
                counterContainer.addMatchedArgumentsCount(methodName);


                // ideally none of the targets should have been frozen
                // but it seems like they are in log4j1 - fixed
                if (summary.mods.anyTargetsAreFrozen()) {
                    return;
                }

                // TODO: Get class in a different way that doesn't break in edge-cases
                if (!summary.context.getDependentStaticFields().isEmpty()) {
                    return;
                }

                // We need to ensure that context and modification information
                // propagates down to other methods that might be recording
                for (String r : recording) {
                    contextMap.get(r).addContextFields(summary.context);
                    modificationMap.get(r).addModificationFields(summary.mods);
                }

                counterContainer.addTotalCalls(methodName);
                logSummaryApplication(methodName, summary);
                summary.mods.applyModifications();

                // at this point we want to make sure that we don't create another summary
                // like the one we just applied
                stopRecording(methodName);

                skipped = true;

                Instruction nextInstruction = call.getNext();
                StackFrame frame = ti.getModifiableTopFrame();
                frame.removeArguments(mi);

                String returnType = mi.getReturnType();
                if (returnType.equals("V")) {
                    ti.skipInstruction(nextInstruction);
                    return;
                }

                Object returnValue = summary.mods.getReturnValue();
                putReturnValueOnStackFrame(returnType, returnValue, frame, vm);
                ti.skipInstruction(nextInstruction);
            }
        }
    }

    private MethodSummary getApplicableSummary(VM vm, ThreadInfo ti, MethodInfo mi, JVMInvokeInstruction call) {
        MethodSummary summary;

        String methodName = mi.getFullName();
        int runningThreads = vm.getThreadList().getCount().alive;
        if (call instanceof INVOKESTATIC) {
            summary = container.hasMatchingContext(methodName, call.getArgumentValues(ti), runningThreads == 1);
        } else {
            StackFrame top = ti.getTopFrame();
            byte[] argTypes = mi.getArgumentTypes();
            Object[] args = top.getArgumentsValues(ti, argTypes);

            ElementInfo calleeObject = ti.getElementInfo(top.getCalleeThis(mi));
            // call.getArgumentValues() throws NPE here in log4j2 orig
            // at line 890 of StackFrame, which is strange cause this is executing the same code
            summary = container.hasMatchingContext(methodName, calleeObject, args, runningThreads == 1);
        }

        if (summary == null) {
            counterContainer.addFailedMatchCount(methodName);
        }

        return summary;
    }

    private void stopRecording(String methodName) {
        contextMap.remove(methodName);
        modificationMap.remove(methodName);
        recording.remove(methodName);
    }

    private void putReturnValueOnStackFrame(String returnType, Object returnValue, StackFrame frame, VM vm) {
        if (returnValue == null) {
            frame.pushRef(MJIEnv.NULL);
        } else if (returnType.equals("J")) {
            frame.pushLong((Long) returnValue);
        } else if (returnType.equals("D")) {
            frame.pushDouble((Double) returnValue);
        } else if (returnType.equals("F")) {
            frame.pushFloat((Float) returnValue);
        } else if (returnType.equals("S")) {
            frame.push((Integer) returnValue);
        } else if (returnType.equals("I")) {
            frame.push((Integer) returnValue);
        } else if (returnType.equals("Z")) {
            if (returnValue instanceof Boolean) {
                boolean flag = (Boolean) returnValue;
                if (flag) {
                    frame.push(0);
                } else {
                    frame.push(1);
                }
            } else {
                frame.push((Integer) returnValue);
            }
            // method returns an object
        } else {
            if (returnValue instanceof ElementInfo) {
                ElementInfo returnObject = (ElementInfo) returnValue;
                frame.pushRef(returnObject.getObjectRef());
            } else {
                if ((Integer) returnValue == MJIEnv.NULL) {
                    return;
                }
                if (vm.getElementInfo((Integer) returnValue) == null) {
                    return;
                }
                frame.pushRef((Integer) returnValue);
            }
        }
    }


    @Override
    public void instructionExecuted(VM vm, ThreadInfo ti, Instruction nextInsn, Instruction executedInsn) {
        MethodInfo mi = executedInsn.getMethodInfo();
        if (skip) {
            if (mi == miMain) {
                skip = false;
            } else {
                return;
            }
        }

        //out.println(executedInsn);
        if (executedInsn instanceof JVMInvokeInstruction) {
            // skipping in executeInstruction still results in
            // this instructionExecuted notification surprisingly
            if (skipped) {
                skipped = false;
                return;
            }

            JVMInvokeInstruction call = (JVMInvokeInstruction) executedInsn;
            mi = call.getInvokedMethod(ti);

            if (mi == null)
                return;

            // if the invocation is blocked, do nothing
            if (ti.getNextPC() == call)
                return;

            String methodName = mi.getFullName();
            int numberOfInstructions = mi.getNumberOfInstructions();

            counterContainer.addMethodInvocation(methodName, numberOfInstructions);


            if (!recorded.contains(methodName)) {
                recording.add(methodName);
            }

            if (methodStopsRecording(mi, methodName)) {
                return;
            }

            int runningThreads = vm.getThreadList().getCount().alive;

            Object[] args = call.getArgumentValues(ti);
            if (!contextMap.containsKey(methodName)) {
                boolean isStatic = executedInsn instanceof INVOKESTATIC;
                byte[] types = mi.getArgumentTypes();
                for (byte type : types) {
                    if (type == Types.T_ARRAY) {
                        blacklistAndResetRecording("array argument");
                        return;
                    }
                }

                if (isStatic) {
                    contextMap.put(methodName, new MethodContext(args, runningThreads == 1));
                } else {
                    if (ti.getElementInfo(call.getLastObjRef()) == null) {
                        blacklistAndResetRecording("faulty this");
                        return;
                    }
                    contextMap.put(methodName, new MethodContext(ti.getElementInfo(call.getLastObjRef()), args, runningThreads == 1));
                }
            }

            if (!modificationMap.containsKey(methodName)) {
                modificationMap.put(methodName, new MethodModifications(args));
            }
        } else if (executedInsn instanceof JVMReturnInstruction) {
            JVMReturnInstruction ret = (JVMReturnInstruction) executedInsn;
            mi = ret.getMethodInfo();
            String methodName = mi.getFullName();

            if (recording.contains(methodName)) {
                Object returnValue = ret.getReturnValue(ti);
                completeRecording(methodName, returnValue);
            }
        } else if (executedInsn instanceof EXECUTENATIVE) {
            if (nativeWhiteList.contains(mi.getName())) {
                return;
            }
            blacklistAndResetRecording("native method");
        } else if (executedInsn instanceof FieldInstruction) {
            String methodName = mi.getFullName();
            if (!recording.contains(methodName))
                return;

            FieldInstruction finsn = (FieldInstruction) executedInsn;
            if (finsn.isRead()) {
                handleReadInstruction(methodName, finsn);
            } else {
                handleWriteInstruction(methodName, finsn);
            }
        }
    }

    private void handleWriteInstruction(String methodName, FieldInstruction finsn) {
        counterContainer.addWriteCount(methodName);

        ElementInfo ei = finsn.getLastElementInfo();
        FieldInfo fi = finsn.getFieldInfo();

        int storageOffset = fi.getStorageOffset();
        assert (storageOffset != -1);

        if (ei.isShared()) {
            blacklistAndResetRecording("shared field write");
            return;
        }

        if (fi.getTypeCode() == Types.T_ARRAY) {
            blacklistAndResetRecording("array field");
            return;
        }

        String type = fi.getType();
        Object valueObject = ei.getFieldValueObject(fi.getName());

        if(fi.isReference()) {
            type = "#objectReference";
            valueObject =ei.getReferenceField(fi.getName());
        }

        if (finsn instanceof PUTFIELD) {
            for (String stackMethodName : recording) {
                modificationMap.get(stackMethodName).addField(finsn.getFieldName(), type, ei, valueObject);
            }

        } else if (finsn instanceof PUTSTATIC) {
            for (String stackMethodName : recording) {
                modificationMap.get(stackMethodName).addStaticField(finsn.getFieldName(), type, fi.getClassInfo(), valueObject);
            }

        }
    }

    private void handleReadInstruction(String methodName, FieldInstruction finsn) {
        counterContainer.addReadCount(methodName);

        FieldInfo fi;
        try {
            fi = finsn.getFieldInfo();
            // thrown if classloader requires a roundtrip
        } catch (LoadOnJPFRequired loadOnJPFRequired) {
            blacklistAndResetRecording("static read");
            return;
        }

        ElementInfo ei = finsn.getLastElementInfo();
        int storageOffset = fi.getStorageOffset();
        assert (storageOffset != -1);

        if (ei.isShared()) {
            blacklistAndResetRecording("shared field read");
            return;
        }

        if (fi.getTypeCode() == Types.T_ARRAY) {
            blacklistAndResetRecording("array field");
            return;
        }

        if (finsn instanceof GETFIELD) {
            // propagate context to all recording methods
            for (String stackMethodName : recording) {
                if (!contextMap.get(stackMethodName).containsField(finsn.getFieldName(), ei)) {
                    contextMap.get(stackMethodName).addField(finsn.getFieldName(), ei, ei.getFieldValueObject(fi.getName()));
                }
            }
        } else if (finsn instanceof GETSTATIC) {
            for (String stackMethodName : recording) {
                if (!contextMap.get(stackMethodName).containsStaticField(finsn.getFieldName()))
                    contextMap.get(stackMethodName).addStaticField(finsn.getFieldName(), fi.getClassInfo(), ei.getFieldValueObject(fi.getName()));
            }
        }
    }

    private void completeRecording(String methodName, Object returnValue) {
        modificationMap.get(methodName).setReturnValue(returnValue);
        if (container.canStoreMoreSummaries(methodName)) {
            container.addSummary(methodName, contextMap.get(methodName), modificationMap.get(methodName));
            contextMap.remove(methodName);
            modificationMap.remove(methodName);
        } else {
            // stop recording "methodName"
            recorded.add(methodName);
        }
        counterContainer.addRecordedMethod(methodName);
        recording.remove(methodName);
    }

    private boolean methodStopsRecording(MethodInfo mi, String methodName) {
        if (mi.getReturnTypeCode() == Types.T_ARRAY) {
            blacklistAndResetRecording("array type");
            return true;
        }

        if (mi.getName().equals("<init>")) {
            blacklistAndResetRecording("<init>");
            return true;
        }

        if (mi.getName().equals("<clinit>")) {
            blacklistAndResetRecording("<clinit>");
            return true;
        }

        // if a method is blacklisted, or is a synthetic method
        // methodName will match mi.getFullName,
        // getName() is used for the manually entered names
        if (blackList.contains(methodName)
                || blackList.contains(mi.getName())
                // LambdaTest.testFreeVariables
                || methodName.contains("$")
                || methodName.contains("$$")
                || methodName.contains("Verify")
                // gov.nasa.jpf.test.java.concurrent.ExecutorServiceTest and CountDownLatchTest
                || methodName.contains("java.util.concurrent.locks")
                || methodName.contains("reflect")) {
            blacklistAndResetRecording("blacklisted");
            return true;
        }

        return false;
    }


    // Search listener part
    @Override
    public void searchStarted(Search search) {
        out.println("----------------------------------- search started");
        reinitialise();
        if (skipInit) {
            ThreadInfo tiCurrent = ThreadInfo.getCurrentThread();
            miMain = tiCurrent.getEntryMethod();
            out.println("      [skipping static init instructions]");
        }
    }

    private void logSummaryApplication(String methodName, MethodSummary summary) {
        out.println("applied summary for " + methodName);
        out.println(summary.context);
        out.println(summary.mods);
    }

    @Override
    public void searchFinished(Search search) {
        out.println("----------------------------------- search finished");
        out.println();
//        out.println(counterContainer.getMethodStatistics());
    }
}
