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
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.LocalVarInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.bytecode.FieldInstruction;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Listener implementing a small method-summary utility.
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


  int replacedCalls = 0;


  static HashMap<String,MethodContext> contextMap = new HashMap<>();
  static HashMap<String,MethodCounter> counterMap = new HashMap<>();
  static HashMap<String,MethodModifications> modificationMap = new HashMap<>();

  boolean skipInit = false;

  MethodInfo lastMi;
  PrintWriter out;

  boolean skip;
  MethodInfo miMain; // just to make init skipping more efficient

  public SummaryCreator (Config config, JPF jpf) {
    /** @jpfoption et.skip_init : boolean - do not log execution before entering main() (default=true). */
    skipInit = config.getBoolean("et.skip_init", true);

    if (skipInit) {
      skip = true;
    }

    blackList.add("hasNext()");
    //blackList.add("<init>");
    //blackList.add("java.lang.String.hashCode()I");
    //blackList.add("java.lang.Class.desiredAssertionStatus()Z");

    // Todo add classnames here
    nativeWhiteList.add("append");
    nativeWhiteList.add("toString");
    nativeWhiteList.add("desiredAssertionStatus");
    nativeWhiteList.add("print");
    nativeWhiteList.add("println");
    nativeWhiteList.add("hashCode");
    nativeWhiteList.add("min");
    nativeWhiteList.add("max");
    // this might not be quite right
    //nativeWhiteList.add("forName");

    out = new PrintWriter(System.out, true);
  }

  public void resetRecording(String nativeMethodName) {
    for(String r : recording) {
      if(recorded.contains(r)) {
        assert(false);
      }

      MethodCounter counter = counterMap.get(r);
      counter.interruptedByNativeCall = true; 
      counter.interruptingMethod = nativeMethodName;
      //out.println("BLACKLISTED " + r);
      blackList.add(r);
    }


    recording = new HashSet<>();
  }

  public void resetRecording() {
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
  public void threadInterrupted(VM vm, ThreadInfo interruptedThread) {
    resetRecording();
  }

  @Override
  public void choiceGeneratorRegistered (VM vm, ChoiceGenerator<?> nextCG, ThreadInfo currentThread, Instruction executedInstruction) {
    resetRecording();
  }
  @Override
  public void choiceGeneratorSet (VM vm, ChoiceGenerator<?> newCG) {
    resetRecording();
  }


  @Override 
  public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {

    if (instructionToExecute instanceof JVMInvokeInstruction) {
      //out.println("executing " + instructionToExecute);
    }
  }

  @Override
  public void instructionExecuted (VM vm, ThreadInfo thread, Instruction nextInsn, Instruction executedInsn) {
    ThreadInfo ti = thread;
    MethodInfo mi = executedInsn.getMethodInfo();

    if (skip) {
      if (mi == miMain) {
        skip = false;
      } else {
        return;
      }
    }
    if (executedInsn instanceof JVMInvokeInstruction) {
      JVMInvokeInstruction call = (JVMInvokeInstruction)executedInsn;
      mi = call.getInvokedMethod(ti);
      
      // this can apparently happen
      if(mi == null)
        return;

      // if the invocation is blocked, do nothing
      if (ti.getNextPC() == call) 
        return;

      String methodName = mi.getFullName();
      
      // this probably needs to be a condition
      // it doesn't seem reasonable to return the same
      // object when we allocate a new instance
      // it does however seem to work!
      // mi.getName().equals("<init>") || 

      if(!counterMap.containsKey(methodName)) {
        counterMap.put(methodName, new MethodCounter(methodName));
      }
      counterMap.get(methodName).totalCalls++;
      counterMap.get(methodName).instructionCount = mi.getNumberOfInstructions();


      if(methodName.equals("java.lang.StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;")) {
        return;
      }

      if(mi.getName().equals("<init>") || mi.getName().equals("<clinit>")) {
        //counterMap.get(methodName).isInit = true;
        resetRecording("<init>");
        return;
      }

      if(blackList.contains(methodName)) {
        resetRecording(methodName);
        return;
      }

      if(!contextMap.containsKey(methodName)) {
        Object[] args = call.getArgumentValues(ti);
        if(executedInsn instanceof INVOKESTATIC) {
          contextMap.put(methodName, new MethodContext(args));
        }else{  
          contextMap.put(methodName, new MethodContext(ti.getElementInfo(call.getLastObjRef()),args));
        }
      }
      
      if(!modificationMap.containsKey(methodName)) {
        //assert(mi.getArgumentsSize() == call.getArgumentValues(ti).length);
        modificationMap.put(methodName, new MethodModifications(call.getArgumentValues(ti)));
      }

      if(!recorded.contains(methodName)) {
        //out.println("recording " + methodName);
        //out.println("starting with " + executedInsn);
        //out.println();
        recording.add(methodName);
      }else{
        MethodContext currentContext =  contextMap.get(methodName);
        MethodCounter counter = counterMap.get(methodName);
        counter.attemptedMatchCount++;

        // adding a fail-trip to avoid doing excessive context-matching
        if(counter.attemptedMatchCount - counter.argsMatchCount > 5 ) {
          return;
        }

        if(executedInsn instanceof INVOKESTATIC) {
          if(!currentContext.match(call.getArgumentValues(ti))) {
            //out.println("context mismatch " + methodName);
            //out.println("context=" + contextMap.get(methodName));
            //out.println();
            return;
          }
        }else{
          //out.println("Matching context " + methodName + " " + contextMap.get(methodName));
          if(!currentContext.match(ti.getElementInfo(call.getLastObjRef()),call.getArgumentValues(ti))) {
            //out.println("context mismatch " + methodName);
            //out.println("context=" + contextMap.get(methodName));
            //out.println();
            return;
          }
        }

        // NOTE: We need to ensure that context information
        // propagates down to other methods that might be recorded
        for(String r : recording) {
          contextMap.get(r).addContextFields(currentContext);
        }


        replacedCalls++;
        counter.argsMatchCount++;
        modificationMap.get(methodName).applyModifications();


        // find the return instruction
        Instruction nextInstruction = mi.getInstruction(mi.getNumberOfInstructions()-1);
        if(nextInstruction instanceof NATIVERETURN) {
          return;
        }
        
        //out.println("applying summary of " + methodName);
        //out.println("context=" + contextMap.get(methodName));
        
        //out.println();
        // no return value necessary
        if(nextInstruction instanceof RETURN) {
          //out.println("applying summary for " + methodName);
          //out.println("context=" + contextMap.get(methodName));
          //out.println();
          StackFrame frame = ti.getModifiableTopFrame();
          frame.removeArguments(mi);
          Object returnValue = modificationMap.get(methodName).getReturnValue();
          assert(returnValue == null);
          ti.skipInstruction(nextInstruction);
          return;
        }
        

        // prepare stack with correct return value
        JVMReturnInstruction ret = (JVMReturnInstruction) nextInstruction;
        StackFrame frame = ti.getModifiableTopFrame();
        Object returnValue = modificationMap.get(methodName).getReturnValue();
        if(returnValue instanceof Long) {
          frame.pushLong((Long) returnValue);
        } else if(returnValue instanceof Double) {
          frame.pushDouble((Double) returnValue);
        } else if(returnValue instanceof ElementInfo) {
          frame.push(((ElementInfo) returnValue).getObjectRef());
        } else {
          if(returnValue == null){
            //out.println(methodName);
            frame.push(MJIEnv.NULL);
          }else{
            if(frame.getSlots().length == 0) {//
              out.println(methodName);
              out.println(contextMap.get(methodName));
              out.println(ret);
              out.println(returnValue);
            }else{
              frame.push((Integer) returnValue);
            }
          }
        }

        //out.println("Skipping to " + nextInstruction);
        ti.skipInstruction(nextInstruction);

      }




    } else if (executedInsn instanceof JVMReturnInstruction) {
      JVMReturnInstruction ret = (JVMReturnInstruction) executedInsn;
      mi = ret.getMethodInfo();
      String methodName = mi.getFullName();
      if(recording.contains(methodName)) {
        recorded.add(methodName);
        modificationMap.get(methodName).setReturnValue(ret.getReturnValue(ti));
        counterMap.get(methodName).recorded = true;
        recording.remove(methodName);
      }
    } else if(executedInsn instanceof EXECUTENATIVE) {
      String methodName = mi.getFullName();
      if(nativeWhiteList.contains(mi.getName())){
        //out.println("CALLED WHITELISTED NATIVE FUNCTION " + mi.getFullName());
        return;
      }


      resetRecording(methodName);
    } else if(executedInsn instanceof FieldInstruction) {
      String methodName = mi.getFullName();
      if(!recording.contains(methodName))
        return;

      FieldInstruction finsn = (FieldInstruction) executedInsn;
      MethodCounter counter = counterMap.get(methodName);
      MethodContext context = contextMap.get(methodName);
      if(finsn.isRead()){
        counter.readCount++;
        ElementInfo ei = finsn.getLastElementInfo();
        FieldInfo fi = finsn.getFieldInfo();
        int storageOffset = fi.getStorageOffset();
        assert(storageOffset != -1);

        if(finsn instanceof GETFIELD) {
          // add to the methods above in the call-stack
          for(String stackMethodName : recording) {
            if(!contextMap.get(stackMethodName).containsField(finsn.getFieldName(), ei)) {
              contextMap.get(stackMethodName).addField(finsn.getFieldName(), ei, ei.getFieldValueObject(fi.getName()));
            }
          }
        }  else if (finsn instanceof GETSTATIC) {
          // add to the methods above in the call-stack
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

        if(finsn instanceof PUTFIELD) {
          for(String stackMethodName : recording) {
            modificationMap.get(stackMethodName).addField(finsn.getFieldName(), ei, ei.getFieldValueObject(fi.getName()));
          }
          
        } else if(finsn instanceof PUTSTATIC) {
          for(String stackMethodName : recording) {
            modificationMap.get(stackMethodName).addStaticField(finsn.getFieldName(), fi.getClassInfo(), ei.getFieldValueObject(fi.getName()));
          }
          
        }
      }
    }
  } 



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
    //recording = new HashSet<String>();
    lastMi = null;
  }

  @Override
  public void stateBacktracked(Search search) {
    //recording = new HashSet<String>();
    lastMi = null;
  }

  public String nativeMethodList() {
    StringBuilder nativeMethods = new StringBuilder();

    nativeMethods.append("{\"native-method-list\":[");
    for(String mName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(mName);
      if(!counter.interruptedByNativeCall) 
        continue;

      nativeMethods.append("\"" + counter.interruptingMethod + "\",");
    }

    nativeMethods.deleteCharAt(nativeMethods.length()-1);
    nativeMethods.append("]}");
    return nativeMethods.toString();
  }

  public String methodStatistics() {
    int uniqueMethods = 0;
    int recordedMethods = 0;
    int initCount = 0;
    int transitionInterrupts = 0;
    int nativeMethodInterrupts = 0;
    StringBuilder methodStats = new StringBuilder();
    methodStats.append("{\"methodStats\":[ ");
    for(String methodName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(methodName);
      MethodContext context = contextMap.get(methodName);
      uniqueMethods++;
      assert(counter != null);
      if(!(counter.recorded || counter.interrupted())) {
        initCount++;
        //assert(counter.isInit);
        continue;
      }


      assert(!(counter.interrupted() && counter.recorded));
      if( counter.totalCalls-1 != 0 && (counter.recorded || counter.interruptedByNativeCall)) {
        methodStats.append("{\"methodName\":\"" + methodName + "\"");
        methodStats.append(", \"counter\":" + counter);
        if(counter.recorded ) {
          recordedMethods++;
          methodStats.append(", \"context\":" + context );
          //out.println("matched " + counter.argsMatchCount + "/" + (counter.totalCalls-1) + " times");
          methodStats.append(", \"mods\":" + modificationMap.get(methodName));
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

    //out.println("We called " + uniqueMethods + " methods.");
    //out.println(recordedMethods + " of these were recorded.");
    //out.println(nativeMethodInterrupts + " were interrupted by Native Method Calls");
    //out.println(transitionInterrupts + " were interrupted by Transitions");
    //out.println(initCount + " were initialisation");
    // fails for boundedBuffer (not critical atm)
    //assert(uniqueMethods == (recordedMethods+nativeMethodInterrupts+transitionInterrupts+initCount));
    return methodStats.toString();
  }

  @Override
  public void searchFinished(Search search) {
    out.println("----------------------------------- search finished");
    out.println();

    out.println(methodStatistics());
    //out.println(nativeMethodList());
  }

}
