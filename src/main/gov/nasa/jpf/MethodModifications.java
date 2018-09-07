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

import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;

import java.util.HashMap;

class MethodModifications {
  public MethodModifications(Object[] args) {
    params = args;
    modifiedFields = new HashMap<>();
    modifiedStaticFields = new HashMap<>();
  }

  private Object[] params;
  private HashMap<Integer,ModifiedFieldData> modifiedFields;
  private HashMap<Integer,ModifiedFieldData> modifiedStaticFields;
  private Object returnValue;

  private class ModifiedFieldData {
    ModifiedFieldData(String fieldName, String type, ElementInfo ei, Object newValue) {
      this.fieldName = fieldName;
      this.type = type;
      targetObject = ei;
      this.newValue = newValue;
    }

    // static field
    ModifiedFieldData(String fieldName, String type, ClassInfo ci, Object newValue) {
      this.fieldName = fieldName;
      this.type = type;
      classInfo = ci;
      this.newValue = newValue;
    }

    String fieldName;
    String type;
    // for non-static fields
    ElementInfo targetObject;
    Object newValue;
    // only needed/valid for Static fields
    ClassInfo classInfo;
  }


  private HashMap<Integer,ModifiedFieldData> getModifiedFields() {
    return modifiedFields;
  }

  private HashMap<Integer,ModifiedFieldData> getModifiedStaticFields() {
    return modifiedStaticFields;
  }

  @Override
  public String toString() {
    // TODO: Distinguish between actually returning null, and void method
    if(params.length == 0 && modifiedFields.size() == 0 && modifiedStaticFields.size() == 0 && returnValue == null) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("{\"modsSize\":").append(1 + params.length + modifiedFields.size() + modifiedStaticFields.size());
    sb.append(", \"returnValue\":\"").append(returnValue).append("\"");
    sb.append(", \"args\":[ ");
    //sb.append("{args:[");
    for(Object arg : params) {
      if(arg != params[params.length-1]){
        sb.append("\"").append(arg).append("\",");
      }else{
        sb.append("\"").append(arg).append("\"");
      }
    }
    sb.append("], \"fields\":[ ");
    for(ModifiedFieldData fieldData : modifiedFields.values()) {
      sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"targetObject\":\"").append(fieldData.targetObject).append("\", \"value\":\"").append(fieldData.newValue).append("\"},");
    }
    sb.deleteCharAt(sb.length()-1);
    sb.append("], \"staticFields\":[ ");

    for(ModifiedFieldData fieldData : modifiedStaticFields.values()) {
      sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"classInfo\":\"").append(fieldData.classInfo).append("\", \"value\":\"").append(fieldData.newValue).append("\"},");
    }
    sb.deleteCharAt(sb.length()-1);
    sb.append("]}");
    return sb.toString();
  }

  public void setReturnValue(Object returnValue) {
    //System.out.println(returnValue);
    this.returnValue = returnValue;
  }

  public Object getReturnValue() {
    return returnValue;
  } 

  private void applyFieldUpdate(String fieldName, String type, ElementInfo ei, Object newValue) {
    //System.out.println("Setting " + ei + "." + fieldName + " to " + newValue);

    // basic types
    if(type.equals("int")) {
      ei.setIntField(fieldName, (Integer) newValue);
    } else if(type.equals("float")) {
      ei.setFloatField(fieldName, (Float) newValue);
    } else if(type.equals("char")) {
      ei.setCharField(fieldName, (char) newValue);
    } else if(type.equals("byte")) {
      ei.setByteField(fieldName, (Byte) newValue);
    } else if(type.equals("double")) {
      ei.setDoubleField(fieldName, (Double) newValue);
    } else if(type.equals("long")) {
      ei.setLongField(fieldName, (Long) newValue);
    } else if(type.equals("short")) {
      ei.setShortField(fieldName, (Short) newValue);
    } else if(type.equals("boolean")) {
      ei.setBooleanField(fieldName, (Boolean) newValue);
    } //else if(type.equals("[")) {
      // might be problematic - see nhandler GSoC issues
      //ei.setArrayField(fieldName, (Array) newValue);
    //}
  }

  public boolean canModifyAllTargets() {
    for(ModifiedFieldData fieldData : modifiedFields.values()) {
      if(fieldData.targetObject.isFrozen())
        return false;
    }
    for(ModifiedFieldData staticFieldData : modifiedStaticFields.values()) {
      ElementInfo targetClassObject = staticFieldData.classInfo.getModifiableStaticElementInfo();
      if(targetClassObject.isFrozen()) 
        return false;
    }

    return true;
  }

  public void applyModifications() {
    for(ModifiedFieldData fieldData : modifiedFields.values()) {
      assert(!fieldData.targetObject.isShared());  
      applyFieldUpdate(fieldData.fieldName, fieldData.type, fieldData.targetObject, fieldData.newValue);
    }

    for(ModifiedFieldData staticFieldData : modifiedStaticFields.values()) {
      ElementInfo targetClassObject = staticFieldData.classInfo.getModifiableStaticElementInfo();
      assert(targetClassObject != null);
      applyFieldUpdate(staticFieldData.fieldName, staticFieldData.type, targetClassObject, staticFieldData.newValue);
    }
  }

  /**
    * Takes another set of modifications and adds them to itself.
    * Needed when a summary is applied during recording.
    **/
  public void addModificationFields(MethodModifications innerMods) {
    HashMap<Integer,ModifiedFieldData> innerFields = innerMods.getModifiedFields();
    HashMap<Integer,ModifiedFieldData> innerStaticFields = innerMods.getModifiedStaticFields();

    for (Integer fieldHash : innerFields.keySet()) {
      ModifiedFieldData fieldData = innerFields.get(fieldHash);
      this.modifiedFields.put(fieldHash, fieldData);
    }

    for (Integer fieldHash : innerStaticFields.keySet()) {
      ModifiedFieldData fieldData = innerStaticFields.get(fieldHash);
      this.modifiedStaticFields.put(fieldHash, fieldData);
    }
  }

  public void addField(String fieldName, String type, ElementInfo ei, Object newValue) {
    modifiedFields.put((fieldName.hashCode()+ei.hashCode()), new ModifiedFieldData(fieldName, type, ei, newValue));
  }


  public void addStaticField(String fieldName, String type, ClassInfo ci, Object newValue) {
    modifiedStaticFields.put((fieldName.hashCode()+ci.hashCode()), new ModifiedFieldData(fieldName, type, ci, newValue));
  }


}