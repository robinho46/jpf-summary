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


  public void applyFieldUpdate(String fieldName, ElementInfo ei, Object newValue) {
    // if reference object?

    // basic types
    if(ei.instanceOf("I")) {
      ei.setIntField(fieldName, (Integer) newValue);
    } else if(ei.instanceOf("F")) {
      ei.setFloatField(fieldName, (Float) newValue);
    } else if(ei.instanceOf("C")) {
      ei.setCharField(fieldName, (char) newValue);
    } else if(ei.instanceOf("B")) {
      ei.setByteField(fieldName, (Byte) newValue);
    } else if(ei.instanceOf("D")) {
      ei.setDoubleField(fieldName, (Double) newValue);
    } else if(ei.instanceOf("J")) {
      ei.setLongField(fieldName, (Long) newValue);
    } else if(ei.instanceOf("S")) {
      ei.setShortField(fieldName, (Short) newValue);
    } else if(ei.instanceOf("Z")) {
      ei.setBooleanField(fieldName, (Boolean) newValue);
    } else if(ei.instanceOf("[")) {
      // might be problematic - see nhandler GSoC issues
      //ei.setArrayField(fieldName, (Array) newValue);
    }
  }

  public void applyModifications() {
    for(String fieldName : modifiedFields.keySet()) {
      ModifiedFieldData fieldData = modifiedFields.get(fieldName);
      applyFieldUpdate(fieldName,fieldData.targetObject, fieldData.newValue);
    }

    for(String staticFieldName : modifiedStaticFields.keySet()) {
      ModifiedFieldData staticFieldData = modifiedStaticFields.get(staticFieldName);
      ElementInfo targetClassObject = staticFieldData.classInfo.getModifiableClassObject();
      applyFieldUpdate(staticFieldName, targetClassObject, staticFieldData.newValue);
    }
  }

  public void addField(String fieldName, ElementInfo ei, Object newValue) {
    modifiedFields.put(fieldName, new ModifiedFieldData(ei, newValue));
  }


  public void addStaticField(String fieldName, ClassInfo ci, Object newValue) {
    modifiedStaticFields.put(fieldName, new ModifiedFieldData(ci, newValue));
  }

  private class ModifiedFieldData {
    public ModifiedFieldData(ElementInfo ei, Object newValue) {
      targetObject = ei;
      this.newValue = newValue;
    }

    // static field
    public ModifiedFieldData(ClassInfo ci, Object newValue) {
      classInfo = ci;
      this.newValue = newValue;
    }


    // for non-static fields
    public ElementInfo targetObject;
    public Object newValue;
    // only needed/valid for Static fields
    public ClassInfo classInfo;
  }


}