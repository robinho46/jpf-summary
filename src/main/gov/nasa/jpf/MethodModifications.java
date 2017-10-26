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

import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ClassInfo;
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
public class MethodModifications {
  public MethodModifications(Object[] args) {
    params = args;
    modifiedFields = new HashMap<>();
    modifiedStaticFields = new HashMap<>();
  }

  private Object[] params;
  private HashMap<String,ModifiedFieldData> modifiedFields;
  private HashMap<String,ModifiedFieldData> modifiedStaticFields;

  private class ModifiedFieldData {
    public ModifiedFieldData(ElementInfo ei, Object newValue) {
      targetObject = ei;
      this.newValue = newValue;
    }


    // for non-static fields
    public ElementInfo targetObject;
    public Object newValue;
    // only needed/valid for Static fields
    public ClassInfo classInfo;
  }


}