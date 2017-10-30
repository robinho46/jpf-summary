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

    nativeWhiteList.add("desiredAssertionStatus");
    nativeWhiteList.add("println");
    nativeWhiteList.add("hashCode");
    nativeWhiteList.add("min");
    nativeWhiteList.add("max");
    // this might not be quite right
    nativeWhiteList.add("forName");

    out = new PrintWriter(System.out, true);
  }

  public void resetRecording(String nativeMethodName) {
    for(String r : recording) {
      MethodCounter counter = counterMap.get(r);
      counter.interruptedByNativeCall = true; 
      counter.interruptingMethod = nativeMethodName;
      blackList.add(r);
    }
    recording = new HashSet<>();
  }

  public void resetRecording() {
    for(String r : recording) {
      MethodCounter counter = counterMap.get(r);
      counter.interruptedByTransition = true; 

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
  public void instructionExecuted (VM vm, ThreadInfo thread, Instruction nextInsn, Instruction executedInsn) {
    ThreadInfo ti;
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
      ti = thread;
      mi = call.getInvokedMethod(ti);
      
      // this can apparently happen
      if(mi == null)
        return;

      // if the invocation is blocked, do nothing
      if (ti.getNextPC() == call) 
        return;

      String methodName = mi.getFullName();
      if(blackList.contains(methodName)) {
        return;
      }

      if(!counterMap.containsKey(methodName)) {
        counterMap.put(methodName, new MethodCounter(methodName));
      }
      counterMap.get(methodName).totalCalls++;
      counterMap.get(methodName).instructionCount = mi.getNumberOfInstructions();

      if(!contextMap.containsKey(methodName)) {
        Object[] args = call.getArgumentValues(ti);
        contextMap.put(methodName, new MethodContext(args));
      }
      
      if(!modificationMap.containsKey(methodName)) {
        modificationMap.put(methodName, new MethodModifications(call.getArgumentValues(ti)));
      }

      if(!recorded.contains(methodName)) {
        recording.add(methodName);
      }else{
        // method has been recorded, so summary exists
        if(!contextMap.get(methodName).match(call.getArgumentValues(ti))) {
          return;
        }
        modificationMap.get(methodName).applyModifications();


        counterMap.get(methodName).argsMatchCount++;
        replacedCalls++;
      }
    } else if (executedInsn instanceof JVMReturnInstruction) {
      Instruction ret = executedInsn;
      mi = ret.getMethodInfo();
      String methodName = mi.getFullName();
      if(recording.contains(methodName)) {
        recorded.add(methodName);
        counterMap.get(methodName).recorded = true;
        recording = new HashSet<>();
      }
    } else if(executedInsn instanceof EXECUTENATIVE) {
      String methodName = mi.getFullName();
      // || mi.getName().equals("forName")
      if(nativeWhiteList.contains(mi.getName()))
        return;


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
            if(contextMap.get(stackMethodName).containsField(finsn.getFieldName()))
              contextMap.get(stackMethodName).addField(finsn.getFieldName(), ei, ei.getFieldValueObject(fi.getName()));
          }
        }  else if (finsn instanceof GETSTATIC) {
          // add to the methods above in the call-stack
          for(String stackMethodName : recording) {
            if(contextMap.get(stackMethodName).containsStaticField(finsn.getFieldName()))
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
    } else {
      // is other instruction
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

  @Override
  public void searchFinished(Search search) {
    out.println("----------------------------------- search finished");
    out.println();


    int recordedMethods = 0;
    int uniqueMethods = 0;
    int savedInstructions = 0;
    int missedInsnsNative = 0;
    int transitionInterrupts = 0;
    int nativeMethodInterrupts = 0;


    out.println();
    out.print("native-method-list:");
    for(String mName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(mName);
      if(!counter.interruptedByNativeCall) 
        continue;

      out.print(counter.interruptingMethod + ",");
    }
    out.println();

    StringBuilder methodStats = new StringBuilder();
    methodStats.append("{methodStats:[");
    for(String methodName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(methodName);
      MethodContext context = contextMap.get(methodName);
      uniqueMethods++;
      assert(counter != null);
      if(!counter.recorded && !counter.interrupted()) {
        // this shouldn't happen, but seems to be the case for some methods
        // particularly <init>
        continue;
      }


      assert(!(counter.interrupted() && counter.recorded));

      if(counter.recorded ) {
        methodStats.append(counter + ",");
        recordedMethods++;
        savedInstructions += counter.instructionCount * (counter.totalCalls-1);
        //String contextString = context.toString();
        /*
        if(!contextString.equals("empty") && counter.totalCalls-1 != 0) {
          out.println(methodName+".context = " + context );
          out.println("matched " + counter.argsMatchCount + "/" + (counter.totalCalls-1) + " times");
        }*/
      }

      if(counter.interruptedByNativeCall){
        nativeMethodInterrupts++;
      }
      if(counter.interruptedByTransition){
        transitionInterrupts++;
      }

      //out.println();
    }
    methodStats.deleteCharAt(methodStats.length()-1);
    methodStats.append("]}");
    out.println(methodStats.toString());


    out.println("replacedCalls " + replacedCalls);
    out.println("Saved instructions (bad approximation) " + savedInstructions);
    out.println();    
    out.println("We called " + uniqueMethods + " methods.");
    out.println(recordedMethods + " of these were recorded.");
    out.println(nativeMethodInterrupts + " were interrupted by Native Method Calls");
    out.println(transitionInterrupts + " were interrupted by Transitions");
    

    
    out.println();

  }

}
