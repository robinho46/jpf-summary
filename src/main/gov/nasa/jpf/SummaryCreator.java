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


  int staticCalls = 0;
  int specialCalls = 0;
  int virtualCalls = 0;
  int interfaceCalls = 0;

  int replacedCalls = 0;


  static HashMap<String,MethodContext> contextMap = new HashMap<>();
  

  
  static HashMap<String,MethodCounter> counterMap = new HashMap<>();
  


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

    out = new PrintWriter(System.out, true);
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

    //out.println(executedInsn);


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
      


      if(executedInsn instanceof INVOKESTATIC) {
        staticCalls++;
      } else if(executedInsn instanceof INVOKEVIRTUAL) {
        virtualCalls++;
      } else if(executedInsn instanceof INVOKESPECIAL) {
        specialCalls++;
      } else if(executedInsn instanceof INVOKEINTERFACE) {
        interfaceCalls++;
      }

      String methodName = mi.getFullName();



      if(!counterMap.containsKey(methodName)) {
        counterMap.put(methodName, new MethodCounter());
      }
      counterMap.get(methodName).totalCalls++;
      counterMap.get(methodName).instructionCount = mi.getNumberOfInstructions();

      if(!contextMap.containsKey(methodName)) {
        Object[] args = call.getArgumentValues(ti);
        contextMap.put(methodName, new MethodContext(args));
      } else {
        if(contextMap.get(methodName).match(call.getArgumentValues(ti)))
          counterMap.get(methodName).argsMatchCount++;          
      }
      // if(mi.getName().equals("<init>") || mi.getName().equals("<clinit>")){
      //   blackList.add(methodName);
      // }

      if(blackList.contains(methodName)) {
        return;
      }

      if(!recorded.contains(methodName)) {
        recording.add(methodName);
      }else{
        replacedCalls++;

        MethodContext context = contextMap.get(methodName);
        Set<String> staticFieldNames = context.getStaticFieldNames();
        Set<String> fieldNames = context.getFieldNames();

        for (String fieldName : staticFieldNames) {
          out.println(fieldName + " used to be " + context.getStaticFieldValue(fieldName));
          String[] arr = fieldName.split("\\.");
          // if classname matches that of the current method
          Object currentValue = mi.getClassInfo().getStaticFieldValueObject(arr[arr.length-1]);
          out.println("Now it's " + currentValue);
          out.println();
        }

        for (String fieldName : fieldNames) {
          out.println(fieldName + " used to be " + context.getFieldValue(fieldName));
          String[] arr = fieldName.split("\\.");
          ElementInfo source = context.getSourceObject(fieldName);

          
          Object currentValue = source.getFieldValueObject(arr[arr.length-1]);
          out.println("Now it's " + currentValue);
          out.println();
          
        }

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


      for(String r : recording) {
        MethodCounter counter = counterMap.get(r);
        counter.interruptingMethod = methodName;
        counter.interruptedByNativeCall = true; 
        blackList.add(r);
      }
      recording = new HashSet<>();
    } else if(executedInsn instanceof FieldInstruction) {
      String methodName = mi.getFullName();
      if(!recording.contains(methodName))
        return;

      FieldInstruction finsn = (FieldInstruction) executedInsn;
      MethodCounter counter = counterMap.get(methodName);
      MethodContext context = contextMap.get(methodName);
      if(finsn.isRead()){
        ElementInfo ei = finsn.getLastElementInfo();
        FieldInfo fi = finsn.getFieldInfo();
        int storageOffset = fi.getStorageOffset();
        if(storageOffset == -1) {
          out.println("THIS SHOULD NEVER HAPPEN");
          return;
        }
        counter.readCount++;

        if(finsn instanceof GETFIELD) {
          if(context.containsField(finsn.getVariableId()))
            return;

          context.addField(finsn.getVariableId(),ei,ei.getFieldValueObject(fi.getName()));

          //out.println(finsn.getVariableId());
          //out.println(context.getFieldValue(finsn.getVariableId()));
        } else if (finsn instanceof GETSTATIC) {
          if(context.containsStaticField(finsn.getVariableId()))
            return;

          context.addStaticField(finsn.getVariableId(),ei.getFieldValueObject(fi.getName()));
          //out.println(finsn.getVariableId());
          //out.println(context.getStaticFieldValue(finsn.getVariableId()));
        }

      } else {
        counter.writeCount++;
      }
    } else {
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


  
    for(String methodName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(methodName);
      assert(counter != null);
      if(!counter.recorded && !counter.interrupted()) {
        out.println(methodName + " was neither recorded nor interrupted...");
        out.println("called "+ counter.totalCalls + " times");
        out.println("skipping");
        out.println();
        continue;
      }

      assert(!(counter.interrupted() && counter.recorded));
      // methods that are only called once are unneccessary
      if(counter.totalCalls == 1) {
        out.println(methodName + " only called once.");
        continue;
      }

      uniqueMethods++;
      if(counter.recorded ) {
      out.println(methodName);
      out.println(counter);
        recordedMethods++;
        // the summary needs to at least repeat reads and writes
        // each write requires two stores and one write
        // we can't summarise the first call to the function
        savedInstructions += (counter.instructionCount-counter.readCount-(3*counter.writeCount))
                              * (counter.totalCalls-1);
      }

      if(counter.interruptedByNativeCall){
        nativeMethodInterrupts++;
        out.println("accounts for " + 
                      ((counter.instructionCount-counter.readCount-(3*counter.writeCount))
                               * (counter.totalCalls-1)) + " instructions");
        missedInsnsNative += (counter.instructionCount-counter.readCount-(3*counter.writeCount))
                               * (counter.totalCalls-1);
      }
      if(counter.interruptedByTransition){
        transitionInterrupts++;
      }

      out.println();
    }

    out.println("Saved instructions (at MOST) " + savedInstructions);
    out.println("We could have saved an additional " + missedInsnsNative + " if we'd managed native calls");
    out.println();    
    out.println("We called " + uniqueMethods + " methods.");
    out.println(recordedMethods + " of these were recorded.");
    out.println(nativeMethodInterrupts + " were interrupted by Native Method Calls");
    out.println(transitionInterrupts + " were interrupted by Transitions");
    
/*
    out.println();
    out.println("Total number of method calls " + (staticCalls+virtualCalls+specialCalls+interfaceCalls));
    out.println("Potentially replaced calls " + replacedCalls);
    out.println("Static calls " + staticCalls);
    out.println("Virtual calls " + virtualCalls);
    out.println("Special calls " + specialCalls);
    out.println("Interface calls " + interfaceCalls);
*/

    /*
    out.println();
    out.print("native-method-list:");
    for(String mName : counterMap.keySet()) {
      MethodCounter counter = counterMap.get(mName);
      if(!counter.interruptedByNativeCall) 
        continue;

      out.print(counter.interruptingMethod + ",");
    }

    out.println();*/

  }

}
