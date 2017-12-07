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
package gov.nasa.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.INVOKESPECIAL;
import gov.nasa.jpf.jvm.bytecode.INVOKEVIRTUAL;
import gov.nasa.jpf.jvm.bytecode.INVOKESTATIC;
import gov.nasa.jpf.jvm.bytecode.GETFIELD;
import gov.nasa.jpf.jvm.bytecode.GETSTATIC;
import gov.nasa.jpf.jvm.bytecode.PUTFIELD;
import gov.nasa.jpf.jvm.bytecode.PUTSTATIC;
import gov.nasa.jpf.jvm.bytecode.INVOKEINTERFACE;
import gov.nasa.jpf.jvm.bytecode.EXECUTENATIVE;
import gov.nasa.jpf.jvm.bytecode.JVMInvokeInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.jvm.bytecode.NATIVERETURN;
import gov.nasa.jpf.jvm.bytecode.RETURN;
import gov.nasa.jpf.jvm.bytecode.DIRECTCALLRETURN;
import gov.nasa.jpf.jvm.bytecode.VirtualInvocation;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.Types;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.LocalVarInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.bytecode.FieldInstruction;
import gov.nasa.jpf.vm.bytecode.StaticFieldInstruction;

import gov.nasa.jpf.vm.LoadOnJPFRequired;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Listener implementing a method-summary utility.
 */
public class SummaryCreator extends ListenerAdapter {
  static final String INDENT = "  ";
  // contains the names of the methods that have been recorded as
  // doing a complete call-return cycle within a single transition
  static HashSet<String> recorded = new HashSet<>();
  // contains the names of the methods currently being recorded
  static HashSet<String> recording = new HashSet<>();
  // contains the names of the methods that should never be recorded
  // this could be because they are interrupted by a transition
  // or because they call native methods that we can't track
  static HashSet<String> blackList = new HashSet<>();
  // contains the names of native method calls that are known not to have 
  // side-effects that can't be captured in the summary
  static HashSet<String> nativeWhiteList = new HashSet<>();

  static SummaryContainer container = new SummaryContainer();
  static HashMap<String,MethodContext> contextMap = new HashMap<>();
  static HashMap<String,MethodCounter> counterMap = new HashMap<>();
  static HashMap<String,MethodModifications> modificationMap = new HashMap<>();

  boolean skipInit = false;
  boolean skipped = false;

  MethodInfo lastMi;
  PrintWriter out;

  boolean skip;
  MethodInfo miMain; // just to make init skipping more efficient
  public SummaryCreator (Config config, JPF jpf) {
    //  @jpfoption et.skip_init : boolean - do not log execution before entering main() (default=true). 
    skipInit = config.getBoolean("et.skip_init", true);
    skipInit = true;
    if (skipInit) {
      skip = true;
    }

    //TEST-gov.nasa.jpf.test.java.concurrent.ExecutorServiceTest
    blackList.add("java.util.concurrent.locks.AbstractOwnableSynchronizer.setExclusiveOwnerThread(Ljava/lang/Thread;)V");

    // pool1 orig - these summaries somehow reduced the state space by 4? 
    // could be because of the data-race?
    //blackList.add("org.apache.commons.pool.impl.CursorableLinkedList$Listable.next()Lorg/apache/commons/pool/impl/CursorableLinkedList$Listable;");
    //blackList.add("org.apache.commons.pool.impl.CursorableLinkedList$Listable.value()Ljava/lang/Object;");
    // Todo add classnames here
    nativeWhiteList.add("append");
    nativeWhiteList.add("desiredAssertionStatus");
    nativeWhiteList.add("print");
    nativeWhiteList.add("println");
    nativeWhiteList.add("min");
    nativeWhiteList.add("max");

    out = new PrintWriter(System.out, true);
    out.println("~Summaries active~");
  }


  public void stopRecording(String reason) {
    for(String r : recording) {
      if(recorded.contains(r)) {
        assert(false);
      }

      MethodCounter counter = counterMap.get(r);
      if(reason.contains("()")) {
        counter.interruptedByNativeCall = true;
      } else {
        counter.interruptedByOther = true;
      }
      counter.reasonForInterruption = reason;
      //out.println("BLACKLISTED " + r);
      blackList.add(r);
    }

    recording = new HashSet<>();
  }

  public void stopRecording() {
    for(String r : recording) {
      if(recorded.contains(r)) {
        assert(false);
      }
      MethodCounter counter = counterMap.get(r);
      counter.interruptedByTransition = true; 
      //out.println("BLACKLISTED " + r);

      blackList.add(r);
    }

    recording = new HashSet<>();
  }


  @Override 
  public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) { 
    MethodInfo mi = instructionToExecute.getMethodInfo();
    
    if(skip || mi == null) {
      return;
    }

    int runningThreads = vm.getThreadList().getCount().alive;
    ThreadInfo ti = currentThread;

    if (instructionToExecute instanceof JVMInvokeInstruction) {
      JVMInvokeInstruction call = (JVMInvokeInstruction) instructionToExecute;
      mi = call.getInvokedMethod();

      
      if(mi == null) {
        return;
      }
      assert( mi != null);
      String methodName = call.getInvokedMethod().getFullName();
      if(container.hasSummary(methodName)) {
        MethodCounter counter = counterMap.get(methodName);
        counter.attemptedMatchCount++;

        MethodSummary summary;
        if(instructionToExecute instanceof INVOKESTATIC) {
          summary = container.hasMatchingContext(methodName, call.getArgumentValues(ti), runningThreads==1);
        }else{
          Object[] args;
          StackFrame top = ti.getTopFrame();
          byte[] argTypes = mi.getArgumentTypes();
          args = top.getArgumentsValues(ti,argTypes);
          // call.getArgumentValues() throws NPE here in log4j2 orig
          // at line 890 of StackFrame, which is strange cause this is executing the same code
          summary = container.hasMatchingContext(methodName, ti.getElementInfo(top.peek(mi.getArgumentsSize()-1)), args,runningThreads==1);
        }

        if(summary == null) {

          counter.failedMatchCount++;
          return;
        }
        counter.attemptedMatchCount = 0;
        counter.argsMatchCount++;

        // ideally none of the targets should have been frozen
        // but it seems like they are in log4j1 - fixed
        if(!summary.mods.canModifyAllTargets()) {
          return;
        }

        // TODO: Get class in a different way that doesn't break
        if(!summary.context.getDependentStaticFields().isEmpty()) {
          return;
        }

        // We need to ensure that context information
        // propagates down to other methods that might be recording
        for(String r : recording) {
          contextMap.get(r).addContextFields(summary.context);
        }

        summary.mods.applyModifications();
        //out.println("applied summary for " + methodName);
        //out.println(summary.context);
        //out.println(summary.mods);

        // at this point we want to make sure that we don't create another summary
        // like the one we just applied
        contextMap.remove(methodName);
        modificationMap.remove(methodName);
        recording.remove(methodName);


        Instruction nextInstruction = call.getNext();
        skipped = true;
        
        int nArgs = mi.getArgumentsSize();
        StackFrame frame = ti.getModifiableTopFrame();
        frame.removeArguments(mi);

        if(mi.getReturnType().equals("V")) {
          ti.skipInstruction(nextInstruction);
          return;
        }

        // prepare stack with correct return value
        Object returnValue = summary.mods.getReturnValue();
        if(returnValue instanceof Long) {
          frame.pushLong((Long) returnValue);
        } else if(returnValue instanceof Double) {
          frame.pushDouble((Double) returnValue);
        } else if(returnValue instanceof Float) {
          frame.pushFloat((Float) returnValue);
        } else if(returnValue instanceof ElementInfo) {
          frame.pushRef(((ElementInfo) returnValue).getObjectRef());
        } else if(returnValue instanceof Boolean) {
          boolean flag = (Boolean) returnValue;
          if(flag) {
            frame.push(0);
          } else {
            frame.push(1);
          }
        } else {
          if(returnValue == null){
            frame.push(MJIEnv.NULL);
          }else{
            frame.push((Integer) returnValue);
          }
        }
        ti.skipInstruction(nextInstruction);
      }
    }
  }
  
  
  @Override
  public void instructionExecuted (VM vm, ThreadInfo ti, Instruction nextInsn, Instruction executedInsn) {
    MethodInfo mi = executedInsn.getMethodInfo();
    if(skipped) {
      //out.println(executedInsn);
      //out.println(nextInsn);
      skipped = false;
      return;
    }
    if (skip) {
      if (mi == miMain) {
        skip = false;
      } else {
        return;
      }
    }

    //out.println(executedInsn);
    if (executedInsn instanceof JVMInvokeInstruction) {
      JVMInvokeInstruction call = (JVMInvokeInstruction)executedInsn;
      mi = call.getInvokedMethod(ti);
      
      // breaks in log4j3 orig
      if(mi == null)
        return;

      // if the invocation is blocked, do nothing
      if (ti.getNextPC() == call)
        return;

      String methodName = mi.getFullName();
      
      if(!counterMap.containsKey(methodName)) {
        counterMap.put(methodName, new MethodCounter(methodName));
        counterMap.get(methodName).instructionCount = mi.getNumberOfInstructions();
      }
      counterMap.get(methodName).totalCalls++;

      if(mi.getReturnTypeCode() == Types.T_ARRAY) {
        stopRecording("array type");
        return;
      }

      if(mi.getName().equals("<init>")) {
        stopRecording("<init>");
        return;
      }

      if(mi.getName().equals("<clinit>")) {
        stopRecording("<clinit>");
        return;
      }

      
      // This might actually be "OK", 
      // if breaking attributes only affects other extensions, not core?
      // Test-gov.nasa.jpf.test.mc.basic.AttrsTest
      if(methodName.equals("java.lang.Integer.intValue()I")) {
        stopRecording("java.lang.Integer.intValue()I");
        return;
      }

      // if a method is blacklisted, or is a synthetic method
      // methodName will match mi.getFullName,
      // getName() is used for the manually entered names
      if(blackList.contains(methodName) 
          || blackList.contains(mi.getName())
          || methodName.contains("reflect")
          || methodName.contains("$")) {
        stopRecording(methodName);
        return;
      }

      int runningThreads = vm.getThreadList().getCount().alive;

      Object[] args = call.getArgumentValues(ti);
      if(!contextMap.containsKey(methodName)) {
        byte[] types = mi.getArgumentTypes();
        for(byte type : types) {
          if(type == Types.T_ARRAY) {
            stopRecording("array argument");
            return;
          }
        }

        if(executedInsn instanceof INVOKESTATIC) {
          contextMap.put(methodName, new MethodContext(args, runningThreads == 1));
        }else{
          if(ti.getElementInfo(call.getLastObjRef()) == null) {
            stopRecording("faulty this");
            return;
          }
          contextMap.put(methodName, new MethodContext(ti.getElementInfo(call.getLastObjRef()),args, runningThreads==1));
        }
      }
      
      if(!modificationMap.containsKey(methodName)) {
        modificationMap.put(methodName, new MethodModifications(args));
      }

      if(!recorded.contains(methodName)) {
        recording.add(methodName);
      }

    } else if (executedInsn instanceof JVMReturnInstruction) {
      JVMReturnInstruction ret = (JVMReturnInstruction) executedInsn;
      mi = ret.getMethodInfo();
      String methodName = mi.getFullName();

      if(recording.contains(methodName)) {
        //recorded.add(methodName);
        modificationMap.get(methodName).setReturnValue(ret.getReturnValue(ti));
        if(container.canStoreMoreSummaries(methodName)) {
          container.addSummary(methodName, contextMap.get(methodName), modificationMap.get(methodName));
          contextMap.remove(methodName);
          modificationMap.remove(methodName);
        } else {
          // stop recording "methodName"
          recorded.add(methodName);
        }
        counterMap.get(methodName).recorded = true;
        recording.remove(methodName);
      }
    } else if(executedInsn instanceof EXECUTENATIVE) {
      String methodName = mi.getFullName();
      if(nativeWhiteList.contains(mi.getName())){
        //out.println("CALLED WHITELISTED NATIVE FUNCTION " + mi.getFullName());
        return;
      }
      stopRecording(methodName);
    } else if(executedInsn instanceof FieldInstruction) {
      String methodName = mi.getFullName();
      if(!recording.contains(methodName))
        return;

      FieldInstruction finsn = (FieldInstruction) executedInsn;
      MethodCounter counter = counterMap.get(methodName);
      MethodContext context = contextMap.get(methodName);
      if(finsn.isRead()){
        counter.readCount++;
        // TODO: Fix this - see comment below
        if(finsn instanceof GETSTATIC) {
          stopRecording("Static read");
          return;
        }
        // this breaks for static, sometimes, presumably the class is not initialized?
        ElementInfo ei = finsn.getLastElementInfo();
        FieldInfo fi = finsn.getFieldInfo();
        int storageOffset = fi.getStorageOffset();
        assert(storageOffset != -1);
        if(ei.isShared()) {
          stopRecording("shared field read");
          return;
        }

        if(finsn instanceof GETFIELD) {
          // propagate context to all recording methods
          for(String stackMethodName : recording) {
            if(!contextMap.get(stackMethodName).containsField(finsn.getFieldName(), ei)) {
              contextMap.get(stackMethodName).addField(finsn.getFieldName(), ei, ei.getFieldValueObject(fi.getName()));
            }
          }
        }  else if (finsn instanceof GETSTATIC) {
          for(String stackMethodName : recording) {
            if(!contextMap.get(stackMethodName).containsStaticField(finsn.getFieldName()))
              contextMap.get(stackMethodName).addStaticField(finsn.getFieldName(),fi.getClassInfo(),ei.getFieldValueObject(fi.getName()));
          }
        }
      } else {
        counter.writeCount++;
        ElementInfo ei = finsn.getLastElementInfo();
        FieldInfo fi = finsn.getFieldInfo();
        int storageOffset = fi.getStorageOffset();
        assert(storageOffset != -1);
        if(ei.isShared()) {
          stopRecording("shared field write");
          return;
        }

        if(fi.getType().charAt(fi.getType().length()-1) == ']') {
          stopRecording("array operation");
        }

        if(finsn instanceof PUTFIELD) {
          for(String stackMethodName : recording) {
            modificationMap.get(stackMethodName).addField(finsn.getFieldName(), fi.getType(), ei, ei.getFieldValueObject(fi.getName()));
          }
          
        } else if(finsn instanceof PUTSTATIC) {
          for(String stackMethodName : recording) {
            modificationMap.get(stackMethodName).addStaticField(finsn.getFieldName(), fi.getType(), fi.getClassInfo(), ei.getFieldValueObject(fi.getName()));
          }
          
        }
      }
    }
  }


  @Override
  public void threadInterrupted(VM vm, ThreadInfo interruptedThread) {
    stopRecording();
  }
  @Override
  public void objectLocked (VM vm, ThreadInfo currentThread, ElementInfo lockedObject) {
    stopRecording();
  }
  @Override
  public void objectUnlocked (VM vm, ThreadInfo currentThread, ElementInfo unlockedObject) {
    stopRecording();
  }
  @Override
  public void objectWait (VM vm, ThreadInfo currentThread, ElementInfo waitingObject) {
    stopRecording();
  }
  @Override
  public void objectNotify (VM vm, ThreadInfo currentThread, ElementInfo notifyingObject) {
    stopRecording();
  }
  @Override
  public void objectNotifyAll (VM vm, ThreadInfo currentThread, ElementInfo notifyingObject) {
    stopRecording();
  }
  @Override
  public void objectExposed (VM vm, ThreadInfo currentThread, ElementInfo fieldOwnerObject, ElementInfo exposedObject) {
    stopRecording();
  }
  @Override
  public void objectShared (VM vm, ThreadInfo currentThread, ElementInfo sharedObject) {
    stopRecording();
  }
  @Override
  public void choiceGeneratorRegistered (VM vm, ChoiceGenerator<?> nextCG, ThreadInfo currentThread, Instruction executedInstruction) {
    stopRecording();
  }
  @Override
  public void choiceGeneratorSet (VM vm, ChoiceGenerator<?> newCG) {
    stopRecording();
  }
  @Override
  public void choiceGeneratorAdvanced (VM vm, ChoiceGenerator<?> currentCG) {
    stopRecording();
  }
  @Override
  public void exceptionThrown(VM vm, ThreadInfo currentThread, ElementInfo thrownException) {
    stopRecording();
  }
  // Search listener part

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
    stopRecording();
    lastMi = null;
  }

  @Override
  public void stateBacktracked(Search search) {
    stopRecording();
    lastMi = null;
  }
  



  @Override
  public void searchFinished(Search search) {
    out.println("----------------------------------- search finished");
    out.println();
    methodStatistics();
    //out.println(methodStatistics());
    //out.println(nativeMethodList());
  }

  public String nativeMethodList() {
    StringBuilder nativeMethods = new StringBuilder();

    nativeMethods.append("{\"native-method-list\":[");
    for(String mName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(mName);
      if(counter.interruptedByTransition) 
        continue;

      nativeMethods.append("\"").append(counter.reasonForInterruption).append("\",");
    }

    nativeMethods.deleteCharAt(nativeMethods.length()-1);
    nativeMethods.append("]}");
    return nativeMethods.toString();
  }

  public String methodStatistics() {
    int uniqueMethods = 0;
    int recordedMethods = 0;
    int transitionInterrupts = 0;
    int nativeMethodInterrupts = 0;
    int savedInstructions = 0;

    StringBuilder methodStats = new StringBuilder();
    methodStats.append("{\"methodStats\":[ ");

    for(String methodName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(methodName);
      MethodContext context = contextMap.get(methodName);
      uniqueMethods++;
      assert(counter != null);
      if(!(counter.recorded || counter.interrupted())) {
        continue;
      }

      // this holds except for our silly heuristic which may mess it up
      //assert(!(counter.interrupted() && counter.recorded));
      if( counter.totalCalls-1 != 0 && (counter.recorded || counter.interruptedByNativeCall)) {
        methodStats.append("{\"methodName\":\"").append(methodName).append("\"");
        methodStats.append(", \"counter\":").append(counter);
        if(counter.recorded ) {
          recordedMethods++;
          methodStats.append(", \"context\":").append(context);
          //out.println("matched " + counter.argsMatchCount + "/" + (counter.totalCalls-1) + " times");
          methodStats.append(", \"mods\":").append(modificationMap.get(methodName));
          savedInstructions += counter.argsMatchCount * counter.instructionCount; 
        }
        methodStats.append("},");

      }

      if(counter.interruptedByNativeCall){
        nativeMethodInterrupts++;
      }
      if(counter.interruptedByTransition){
        transitionInterrupts++;
      }
    }
    methodStats.deleteCharAt(methodStats.length()-1);
    methodStats.append("]}");
    out.println("Saved " + savedInstructions + " instructions");
    return methodStats.toString();
  }
}
