package gov.nasa.jpf;

import gov.nasa.jpf.vm.ElementInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Helper class that provides the ability to store multiple summaries for a single method.
 */
class SummaryContainer {
    private Map<String, List<MethodSummary>> container;
    // the maximum number of contexts which we capture
    private static final int CAPACITY = 100;

    SummaryContainer() {
        container = new HashMap<>();
    }

    void addSummary(String methodName, MethodContext context, MethodModifications mods) {
        List<MethodSummary> summaries = container.get(methodName);
        if (summaries == null) {
            summaries = new ArrayList<>();
            summaries.add(new MethodSummary(context, mods));
            container.put(methodName, summaries);
            return;
        }
        if (summaries.size() < CAPACITY) {
            summaries.add(new MethodSummary(context, mods));
            return;
        }

        throw new IndexOutOfBoundsException("Trying to add too many summaries for " + methodName);
    }

    boolean canStoreMoreSummaries(String methodName) {
        List<MethodSummary> summaries = container.get(methodName);
        return summaries == null || summaries.size() < CAPACITY;
    }

    boolean hasSummariesForMethod(String methodName) {
        List<MethodSummary> summaries = container.get(methodName);
        return summaries != null && summaries.size() > 0;
    }

    MethodSummary hasMatchingContext(String methodName, ElementInfo calleeObject, Object[] args, boolean runningAlone) {
        List<MethodSummary> summaries = container.get(methodName);
        if (summaries == null) {
            return null;
        }

        for (MethodSummary summary : summaries) {
            if (summary.context.match(calleeObject, args, runningAlone)) {
                return summary;
            }
        }
        return null;
    }

    MethodSummary hasMatchingContext(String methodName, Object[] args, boolean runningAlone) {
        List<MethodSummary> summaries = container.get(methodName);
        if (summaries == null) {
            return null;
        }

        for (MethodSummary summary : summaries) {
            if (summary.context.match(args, runningAlone)) {
                return summary;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"summaries\":[");
        for (String methodName : container.keySet() ) {
            sb.append("{\"" + methodName + "\":[");
            for(MethodSummary summary : container.get(methodName)) {
                sb.append("{");
                sb.append("\"context\":");
                sb.append(summary.context);
                sb.append(",\"modifications\":");
                sb.append(summary.mods);
                sb.append("},");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("]},");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]}");
        return sb.toString();
    }
}