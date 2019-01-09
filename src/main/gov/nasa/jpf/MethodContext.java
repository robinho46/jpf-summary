package gov.nasa.jpf;

import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;

import java.util.HashMap;

class MethodContext {

    private Object[] params;
    private boolean runningAlone;

    private ElementInfo _this;
    // We need to track Objectref, FieldName, Type(?), Value
    private HashMap<Integer, DependentFieldData> dependentFields;
    private HashMap<String, DependentFieldData> dependentStaticFields;

    private class DependentFieldData {
        String fieldName;
        // for non-static fields
        ElementInfo sourceObject;
        Object previousValue;
        // only needed/valid for Static fields
        ClassInfo classInfo;

        // for non-static fields
        DependentFieldData(String fieldName, ElementInfo ei, Object previousValue) {
            this.fieldName = fieldName;
            sourceObject = ei;
            this.previousValue = previousValue;
        }

        // for static fields
        DependentFieldData(String fieldName, ClassInfo ci, Object previousValue) {
            this.fieldName = fieldName;
            classInfo = ci;
            this.previousValue = previousValue;
        }

        public String toString() {
            return sourceObject.toString() + " " + previousValue.toString();
        }
    }

    public MethodContext(Object[] args, boolean runningAlone) {
        this.runningAlone = runningAlone;
        params = args;
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof ElementInfo) {
                ElementInfo ei = (ElementInfo) params[i];
                if (ei.isStringObject()) {
                    params[i] = ei.asString();
                }
            }
        }
        dependentFields = new HashMap<>();
        dependentStaticFields = new HashMap<>();
    }

    public MethodContext(ElementInfo _this, Object[] args, boolean runningAlone) {
        this.runningAlone = runningAlone;
        this._this = _this;
        params = args;
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof ElementInfo) {
                ElementInfo ei = (ElementInfo) params[i];
                if (ei.isStringObject()) {
                    params[i] = ei.asString();
                }
            }
        }
        dependentFields = new HashMap<>();
        dependentStaticFields = new HashMap<>();
    }

    /**
     * Takes another context and adds all fields from that to itself.
     * Needed when a summary is applied during recording.
     * TODO: Resolve conflicts between contexts.
     * TODO: Add *this* from inner as well, as a field?
     **/
    public void addContextFields(MethodContext innerContext) {
        HashMap<Integer, DependentFieldData> innerFields = innerContext.getDependentFields();
        HashMap<String, DependentFieldData> innerStaticFields = innerContext.getDependentStaticFields();

        for (Integer fieldHash : innerFields.keySet()) {
            DependentFieldData fieldData = innerFields.get(fieldHash);
            this.dependentFields.put(fieldHash, fieldData);
        }

        for (String fieldName : innerStaticFields.keySet()) {
            DependentFieldData fieldData = innerStaticFields.get(fieldName);
            this.dependentStaticFields.put(fieldName, fieldData);
        }
    }


    public boolean match(ElementInfo _this, Object[] args, boolean runningAlone) {
        assert (this._this != null);
        if (this._this != _this || !this._this.equals(_this)) {
            //System.out.println("this mismatch");
            //System.out.println(this._this + " != " + _this);
            return false;
        }

        return match(args, runningAlone);
    }

    public boolean match(Object[] args, boolean runningAlone) {
        if (this.runningAlone != runningAlone)
            return false;
        if (!argumentsMatch(args)) {
            //System.out.println("args mismatch");
            return false;
        }

        // at this point we know that the arguments match,
        // so any field operations that access fields
        // of arguments are safe
        if (!staticFieldsMatch()) {
            //System.out.println("static fields mismatch");
            return false;
        }

        // now both args and static fields are guaranteed to match
        return fieldsMatch();
    }

    private boolean valuesEqual(Object oldValue, Object currentValue) {
        if (oldValue == null) {
            if (currentValue == null) {
                return true;
            } else {
                return false;
            }
        }

        if (oldValue instanceof String) {
            ElementInfo curr = (ElementInfo) currentValue;
            if (curr == null) {
                return false;
            }
            if (curr.isStringObject()) {
                return curr.equalsString((String) oldValue);
            }
        }
        return oldValue.equals(currentValue);
    }

    private boolean fieldsMatch() {
        for (DependentFieldData fieldData : dependentFields.values()) {
            Object oldValue = fieldData.previousValue;
            Object currentValue = fieldData.sourceObject.getFieldValueObject(fieldData.fieldName);
            if (!valuesEqual(oldValue, currentValue)) {/*
        System.out.println("fieldName="+fieldData.fieldName);
        System.out.println("sourceObject="+fieldData.sourceObject);
        System.out.println(oldValue + "!=" + currentValue);
        System.out.println(oldValue == currentValue);
        System.out.println(oldValue.equals(currentValue));*/
                return false;
            }
        }

        return true;
    }

    private boolean staticFieldsMatch() {
        //assert(dependentStaticFields.isEmpty());
        for (String fieldName : dependentStaticFields.keySet()) {
            DependentFieldData fieldData = dependentStaticFields.get(fieldName);
            ClassInfo ci = fieldData.classInfo;
            Object oldValue = fieldData.previousValue;
            // sometimes throws NPE, presumably the ci is not what we want here
            Object currentValue = ci.getStaticFieldValueObject(fieldName);

            if (!valuesEqual(oldValue, currentValue))
                return false;
        }

        return true;
    }

    private boolean argumentsMatch(Object[] args) {
        if (args.length != params.length) {
            throw new IllegalArgumentException("Calling method with wrong number of arguments.");
        }

        for (int i = 0; i < args.length; i++) {
            if (!valuesEqual(params[i], args[i]))
                return false;
        }

        return true;
    }

    public boolean containsField(String fieldName, ElementInfo source) {
        return dependentFields.containsKey((fieldName + source.toString()).hashCode());
    }

    public boolean containsStaticField(String fieldName) {
        return dependentStaticFields.containsKey(fieldName);
    }

    public void addField(String fieldName, ElementInfo source, Object value) {
        if (source.isShared()) {
            System.out.println("READING FROM SHARED OBJECT " + source);
        }
        assert (!source.isShared());

        dependentFields.put((fieldName + source.toString()).hashCode(), new DependentFieldData(fieldName, source, value));
    }

    public void addStaticField(String fieldName, ClassInfo ci, Object value) {
        dependentStaticFields.put(fieldName, new DependentFieldData(fieldName, ci, value));
    }

    private HashMap<Integer, DependentFieldData> getDependentFields() {
        return dependentFields;
    }

    public HashMap<String, DependentFieldData> getDependentStaticFields() {
        return dependentStaticFields;
    }


    @Override
    public String toString() {
        if (params.length == 0 && dependentFields.size() == 0 && dependentStaticFields.size() == 0 && _this == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"contextSize\":").append(1 + params.length + dependentFields.size() + dependentStaticFields.size());
        sb.append(", \"this\":\"").append(_this).append("\"");
        sb.append(", \"args\":[");
        for (Object arg : params) {
            if (arg != params[params.length - 1]) {
                sb.append("\"").append(arg).append("\",");
            } else {
                sb.append("\"").append(arg).append("\"");
            }
        }
        sb.append("], \"fields\":[ ");
        for (DependentFieldData fieldData : dependentFields.values()) {
            sb.append("{\"sourceObject\":\"").append(fieldData.sourceObject).append("\", \"fieldName\":\"").append(fieldData.fieldName).append("\", \"value\":\"").append(fieldData.previousValue).append("\"},");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("], \"staticFields\":[ ");

        for (String fieldName : dependentStaticFields.keySet()) {
            DependentFieldData fieldData = dependentStaticFields.get(fieldName);
            sb.append("{\"fieldName\":\"").append(fieldData.fieldName).append("\", \"classInfo\":\"").append(fieldData.classInfo).append("\", \"value\":\"").append(fieldData.previousValue).append("\"},");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]}");
        return sb.toString();
    }

}