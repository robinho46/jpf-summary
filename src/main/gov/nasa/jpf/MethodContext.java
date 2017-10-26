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
public class MethodContext {
  public MethodContext(Object[] args) {
    params = args;
    dependentFields = new HashMap<>();
    dependentStaticFields = new HashMap<>();
  }

  public boolean match(Object[] args, MethodInfo mi) {
    if(!argumentsMatch(args))
      return false;

    // at this point we know that the arguments match, 
    // so any field operations that access fields
    // of arguments are safe
    if(!staticFieldsMatch())
      return false;

    // now both args and static fields are guaranteed to match
    if(!fieldsMatch())
      return false;

    return true;
  }

  private boolean valuesEqual(Object oldValue, Object currentValue) {
      if(oldValue == null) {
        if(currentValue == null)
          return true;
        else
          return false;
      }

      return oldValue.equals(currentValue);
  }

  private boolean fieldsMatch() {
    for(String fieldName : dependentFields.keySet()) {
      DependentFieldData fieldData = dependentFields.get(fieldName);
      Object oldValue = fieldData.previousValue;
      Object currentValue = fieldData.sourceObject.getFieldValueObject(fieldName);
      if(!valuesEqual(oldValue,currentValue))
        return false;
    }

    return true;
  }

  private boolean staticFieldsMatch() {
    for(String fieldName : dependentStaticFields.keySet()) {
      DependentFieldData fieldData = dependentFields.get(fieldName);
      ClassInfo ci = fieldData.classInfo;
      Object oldValue = fieldData.previousValue;
      Object currentValue = ci.getStaticFieldValueObject(fieldName);

      if(!valuesEqual(oldValue,currentValue))
        return false;
    }

    return true;
  }

  private boolean argumentsMatch(Object[] args) {
    if(args.length != params.length) {
      // throw new exception?
      return false;
    }

    for(int i=0; i<args.length; i++) {
      if(!valuesEqual(args[i],params[i]))
        return false;
    }

    return true;
  }

  public boolean containsField(String fieldName) {
    return dependentFields.containsKey(fieldName);
  }

  public boolean containsStaticField(String fieldName) {
    return dependentStaticFields.containsKey(fieldName);
  }

  public void addField(String fieldName, ElementInfo source, Object value) {
    dependentFields.put(fieldName, new DependentFieldData(source, value));
  }

  public void addStaticField(String fieldName, Object value, ClassInfo ci) {
    dependentStaticFields.put(fieldName,new DependentFieldData( value, ci));
  }


  private Object[] params;
  // We need to track Objectref, FieldName, Type(?), Value 
  private HashMap<String,DependentFieldData> dependentFields;
  private HashMap<String,DependentFieldData> dependentStaticFields;

  private class DependentFieldData {
    public DependentFieldData(ElementInfo ei, Object previousValue) {
      sourceObject = ei;
      this.previousValue = previousValue;
    }

    // for static fields
    public DependentFieldData(Object previousValue, ClassInfo ci) {
      this.previousValue = previousValue;
      classInfo = ci;
    }

    public String toString() {
      return sourceObject.toString() + " " + previousValue.toString();
    }

    // This might be a mistake
    // It will probably not be easily testable
    // It will also reduce the applicability somewhat,
    // as now we must ensure that it's the *same* object
    // this means args must match exactly (including *this*).
    //
    // If it's a reference to a reference... We may have issues. We'll see
    // for non-static fields
    public ElementInfo sourceObject;
    public Object previousValue;
    // only needed/valid for Static fields
    public ClassInfo classInfo;
  }
}