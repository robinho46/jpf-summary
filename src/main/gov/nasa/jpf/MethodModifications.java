package gov.nasa.jpf;

import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

class MethodModifications {
    MethodModifications(Object[] args) {
        params = args;
        modifiedFields = new HashMap<>();
        modifiedStaticFields = new HashMap<>();
    }

    private Object[] params;
    private HashMap<Integer, ModifiedFieldData> modifiedFields;
    private HashMap<Integer, ModifiedFieldData> modifiedStaticFields;
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
        // for static fields
        ClassInfo classInfo;
    }


    private HashMap<Integer, ModifiedFieldData> getModifiedFields() {
        return modifiedFields;
    }

    private HashMap<Integer, ModifiedFieldData> getModifiedStaticFields() {
        return modifiedStaticFields;
    }

    @Override
    public String toString() {
        // TODO: Distinguish between actually returning null, and void method
        if (params.length == 0 && modifiedFields.size() == 0 && modifiedStaticFields.size() == 0 && returnValue == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"modsSize\":").append(1 + params.length + modifiedFields.size() + modifiedStaticFields.size());
        sb.append(", \"returnValue\":\"").append(returnValue).append("\"");
        sb.append(", \"args\":[");
        for (Object arg : params) {
            if (arg != params[params.length - 1]) {
                sb.append("\"").append(arg).append("\",");
            } else {
                sb.append("\"").append(arg).append("\"");
            }
        }
        sb.append("], \"fields\":[ ");
        for (ModifiedFieldData fieldData : modifiedFields.values()) {
            sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"targetObject\":\"").append(fieldData.targetObject).append("\", \"value\":\"").append(fieldData.newValue).append("\"},");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("], \"staticFields\":[ ");

        for (ModifiedFieldData fieldData : modifiedStaticFields.values()) {
            sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"classInfo\":\"").append(fieldData.classInfo).append("\", \"value\":\"").append(fieldData.newValue).append("\"},");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]}");
        return sb.toString();
    }

    void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    Object getReturnValue() {
        return returnValue;
    }

    boolean anyTargetsAreFrozen() {
        for (ModifiedFieldData fieldData : modifiedFields.values()) {
            if (fieldData.targetObject.isFrozen())
                return true;
        }
        for (ModifiedFieldData staticFieldData : modifiedStaticFields.values()) {
            ElementInfo targetClassObject = staticFieldData.classInfo.getModifiableStaticElementInfo();
            if (targetClassObject.isFrozen())
                return true;
        }

        return false;
    }

    void applyModifications() {
        applyFieldUpdates();
        applyStaticFieldUpdates();
    }

    private void applyStaticFieldUpdates() {
        for (ModifiedFieldData staticFieldData : modifiedStaticFields.values()) {
            ElementInfo targetClassObject = staticFieldData.classInfo.getModifiableStaticElementInfo();
            assert (targetClassObject != null);
            applyFieldUpdate(staticFieldData.fieldName, staticFieldData.type, targetClassObject, staticFieldData.newValue);
        }
    }

    private void applyFieldUpdates() {
        for (ModifiedFieldData fieldData : modifiedFields.values()) {
            assert (!fieldData.targetObject.isShared());
            applyFieldUpdate(fieldData.fieldName, fieldData.type, fieldData.targetObject, fieldData.newValue);
        }
    }

    private void applyFieldUpdate(String fieldName, String type, ElementInfo ei, Object newValue) {
        switch (type) {
            case "int":
                ei.setIntField(fieldName, (int) newValue);
                break;
            case "float":
                ei.setFloatField(fieldName, (float) newValue);
                break;
            case "char":
                ei.setCharField(fieldName, (char) newValue);
                break;
            case "byte":
                ei.setByteField(fieldName, (byte) newValue);
                break;
            case "double":
                ei.setDoubleField(fieldName, (double) newValue);
                break;
            case "long":
                ei.setLongField(fieldName, (long) newValue);
                break;
            case "short":
                ei.setShortField(fieldName, (short) newValue);
                break;
            case "boolean":
                ei.setBooleanField(fieldName, (boolean) newValue);
                break;
            case "#objectReference":
                ei.setReferenceField(fieldName, (int) newValue);
                break;
        }
    }

    /**
     * Takes another set of modifications and adds them to itself.
     * Needed when a summary is applied during recording.
     **/
    void addModificationFields(MethodModifications innerMods) {
        HashMap<Integer, ModifiedFieldData> innerFields = innerMods.getModifiedFields();
        HashMap<Integer, ModifiedFieldData> innerStaticFields = innerMods.getModifiedStaticFields();

        for (Integer fieldHash : innerFields.keySet()) {
            ModifiedFieldData fieldData = innerFields.get(fieldHash);
            this.modifiedFields.put(fieldHash, fieldData);
        }

        for (Integer fieldHash : innerStaticFields.keySet()) {
            ModifiedFieldData fieldData = innerStaticFields.get(fieldHash);
            this.modifiedStaticFields.put(fieldHash, fieldData);
        }
    }

    void addField(String fieldName, String type, ElementInfo ei, Object newValue) {
        modifiedFields.put((fieldName.hashCode() + ei.hashCode()), new ModifiedFieldData(fieldName, type, ei, newValue));
    }


    void addStaticField(String fieldName, String type, ClassInfo ci, Object newValue) {
        modifiedStaticFields.put((fieldName.hashCode() + ci.hashCode()), new ModifiedFieldData(fieldName, type, ci, newValue));
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodModifications that = (MethodModifications) o;
        return Arrays.equals(params, that.params) &&
                modifiedFields.equals(that.modifiedFields) &&
                modifiedStaticFields.equals(that.modifiedStaticFields) &&
                returnValue.equals(that.returnValue);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(modifiedFields, modifiedStaticFields, returnValue);
        result = 31 * result + Arrays.hashCode(params);
        return result;
    }
}