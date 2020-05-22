package gov.nasa.jpf;

import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

class MethodContext {

    private Object[] params;
    private boolean runningAlone;

    private ElementInfo calleeObject;
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

    MethodContext(ElementInfo calleeObject, Object[] args, boolean runningAlone) {
        this.runningAlone = runningAlone;
        this.calleeObject = calleeObject;
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
    void addContextFields(MethodContext innerContext) {
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


    boolean match(ElementInfo calleeObject, Object[] args, boolean runningAlone) {
        assert (this.calleeObject != null);
        if (this.calleeObject != calleeObject) {
            return false;
        }

        return match(args, runningAlone);
    }

    boolean match(Object[] args, boolean runningAlone) {
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
            return false;
        }

        // now both args and static fields are guaranteed to match
        return fieldsMatch();
    }

    private boolean valuesDiffer(Object oldValue, Object currentValue) {
        if (oldValue == null) {
            return currentValue != null;
        }

        if (oldValue instanceof String) {
            ElementInfo curr = (ElementInfo) currentValue;
            if (curr == null) {
                return true;
            }
            if (curr.isStringObject()) {
                return !curr.equalsString((String) oldValue);
            }
        }
        return !oldValue.equals(currentValue);
    }

    private boolean fieldsMatch() {
        for (DependentFieldData fieldData : dependentFields.values()) {
            Object oldValue = fieldData.previousValue;
            Object currentValue = fieldData.sourceObject.getFieldValueObject(fieldData.fieldName);
            if (valuesDiffer(oldValue, currentValue)) {
                return false;
            }
        }

        return true;
    }

    private boolean staticFieldsMatch() {
        for (String fieldName : dependentStaticFields.keySet()) {
            DependentFieldData fieldData = dependentStaticFields.get(fieldName);
            ClassInfo ci = fieldData.classInfo;
            Object oldValue = fieldData.previousValue;
            // sometimes throws NPE, presumably the ci is not what we want here
            Object currentValue = ci.getStaticFieldValueObject(fieldName);

            if (valuesDiffer(oldValue, currentValue))
                return false;
        }

        return true;
    }

    private boolean argumentsMatch(Object[] args) {
        if (args.length != params.length) {
            throw new IllegalArgumentException("Calling method with wrong number of arguments.");
        }

        for (int i = 0; i < args.length; i++) {
            if (valuesDiffer(params[i], args[i]))
                return false;
        }

        return true;
    }

    boolean containsField(String fieldName, ElementInfo source) {
        return dependentFields.containsKey((fieldName + source.toString()).hashCode());
    }

    public boolean containsStaticField(String fieldName) {
        return dependentStaticFields.containsKey(fieldName);
    }

    void addField(String fieldName, ElementInfo source, Object value) {
        assert (!source.isShared());

        dependentFields.put((fieldName + source.toString()).hashCode(), new DependentFieldData(fieldName, source, value));
    }

    public void addStaticField(String fieldName, ClassInfo ci, Object value) {
        dependentStaticFields.put(fieldName, new DependentFieldData(fieldName, ci, value));
    }

    private HashMap<Integer, DependentFieldData> getDependentFields() {
        return dependentFields;
    }

    HashMap<String, DependentFieldData> getDependentStaticFields() {
        return dependentStaticFields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodContext context = (MethodContext) o;
        return runningAlone == context.runningAlone &&
                Arrays.equals(params, context.params) &&
                calleeObject.equals(context.calleeObject) &&
                dependentFields.equals(context.dependentFields) &&
                dependentStaticFields.equals(context.dependentStaticFields);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(runningAlone, calleeObject, dependentFields, dependentStaticFields);
        result = 31 * result + Arrays.hashCode(params);
        return result;
    }

    @Override
    public String toString() {
        if (params.length == 0 && dependentFields.size() == 0 && dependentStaticFields.size() == 0 && calleeObject == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"contextSize\":").append(1 + params.length + dependentFields.size() + dependentStaticFields.size());
        sb.append(", \"this\":\"").append(calleeObject).append("\"");
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