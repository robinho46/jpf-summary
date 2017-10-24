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

  // currently only checks the arguments to the function
  public boolean match(Object[] args) {
    if(args.length != params.length) {
      // throw new exception?
      return false;
    }

    for(int i=0; i<args.length; i++) {
      if(args[i] == null) {
        if(params[i] != null)
          return false;
        
        continue;
      }
      
      if(!args[i].equals(params[i]))
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
    dependentFields.put(fieldName, new FieldData(source, value));
  }

  public void addStaticField(String fieldName, Object value) {
    dependentStaticFields.put(fieldName,value);
  }

  public Set<String> getStaticFieldNames() {
    return dependentStaticFields.keySet();
  }

  public Set<String> getFieldNames() {
    return dependentFields.keySet();
  }

  public Object getStaticFieldValue(String fieldName) {
    return dependentStaticFields.get(fieldName);
  }

  public Object getFieldValue(String fieldName) {
    if(!dependentFields.containsKey(fieldName))
      return null;
    return dependentFields.get(fieldName).previousValue;
  }

  public ElementInfo getSourceObject(String fieldName) {
    if(!dependentFields.containsKey(fieldName))
      return null;
    return dependentFields.get(fieldName).sourceObject; 
  }

  private Object[] params;
  // We need to track Objectref, FieldName, Type(?), Value 
  private HashMap<String,FieldData> dependentFields;
  private HashMap<String,Object> dependentStaticFields;

  private class FieldData {
    public FieldData(ElementInfo ei, Object val) {
      sourceObject = ei;
      previousValue = val;
    }

    public String toString() {
      return sourceObject.toString() + " " + previousValue.toString();
    }

    // This might be a massive mistake
    // It will probably not be easily testable
    // It will also reduce the applicability somewhat,
    // as now we must ensure that it's the *same* object
    // this means args must match exactly (including *this*).
    //
    // If it's a reference to a reference... We may have issues. We'll see
    public ElementInfo sourceObject;
    public Object previousValue;
  }
}