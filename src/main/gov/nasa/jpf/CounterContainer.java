package gov.nasa.jpf;

import java.util.HashMap;
import java.util.Map;


/**
 * Wrapper class for maintaining statistics about the methods that have/haven't been recorded.
 */
class CounterContainer {
    private Map<String, MethodCounter> counterMap = new HashMap<>();

    int getAttemptedMatchCount(String methodName) {
        return counterMap.get(methodName).attemptedMatchCount;
    }

    String getMethodStatistics() {
        StringBuilder methodStats = new StringBuilder();
        methodStats.append("{\"methodStats\":[ ");
        for (MethodCounter counter : counterMap.values()) {
            methodStats.append(counter);
            methodStats.append(",");
        }

        methodStats.deleteCharAt(methodStats.length() - 1);
        methodStats.append("]}");
        return methodStats.toString();

    }

    int getNumberOfRecordedMethods() {
        return (int) counterMap.values().stream().filter(MethodCounter::isRecorded).count();
    }

    int getNumberOfUniqueMethods() {
        return counterMap.size();
    }

    void countInterruptedRecording(String methodName, String reason) {
        MethodCounter counter = counterMap.get(methodName);
        if (counter.reasonForInterruption.equals(""))
            counter.reasonForInterruption = reason;
    }

    void overrideReasonForInterruption(String methodName) {
        counterMap.get(methodName).reasonForInterruption = "transition or lock";
    }

    void countAttemptedSummaryMatch(String methodName) {
        counterMap.get(methodName).attemptedMatchCount++;
    }

    void addFailedMatchCount(String methodName) {
        counterMap.get(methodName).failedMatchCount++;
    }

    void addMatchedArgumentsCount(String methodName) {
        counterMap.get(methodName).attemptedMatchCount = 0;
        counterMap.get(methodName).argsMatchCount++;
    }

    void addTotalCalls(String methodName) {
        counterMap.get(methodName).totalCalls++;
    }

    void addMethodInvocation(String methodName, int numberOfInstructions) {
        if (!counterMap.containsKey(methodName)) {
            counterMap.put(methodName, new MethodCounter(methodName));
            counterMap.get(methodName).instructionCount = numberOfInstructions;
        }
        counterMap.get(methodName).totalCalls++;
    }

    void addWriteCount(String methodName) {
        counterMap.get(methodName).writeCount++;
    }

    void addReadCount(String methodName) {
        counterMap.get(methodName).readCount++;
    }

    void addRecordedMethod(String methodName) {
        counterMap.get(methodName).recorded = true;
    }
}
