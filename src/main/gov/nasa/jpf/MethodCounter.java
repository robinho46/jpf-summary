package gov.nasa.jpf;

class MethodCounter {
    MethodCounter(String methodName) {
        this.methodName = methodName;
        totalCalls = 0;
        recorded = false;
        readCount = 0;
        writeCount = 0;
        instructionCount = 0;
        argsMatchCount = 0;
        attemptedMatchCount = 0;
        failedMatchCount = 0;
        reasonForInterruption = "";
    }


    @Override
    public String toString() {
        String str = "{\"methodName\":\"" + methodName + "\"";
        str += ",\"totalCalls\":" + totalCalls;
        str += ",\"argsMatchCount\":" + argsMatchCount;
        str += ",\"instructionCount\":" + instructionCount;
        str += ",\"recorded\":" + recorded;
        str += ",\"interruption\":\"" + reasonForInterruption + "\"";
        str += "}";
        return str;
    }

    boolean isRecorded() {
        return recorded;
    }

    public String toReadableString() {
        String str = "called " + totalCalls + " times";
        if (readCount > 0)
            str += "\nreadCount=" + readCount;
        if (writeCount > 0)
            str += "\nwriteCount=" + writeCount;

        str += "\ninstructionCount=" + instructionCount;

        if (argsMatchCount != 0) {
            str += "\nArgs matched " + argsMatchCount + " times";
        }

        return str;
    }

    private String methodName;

    int totalCalls;

    // true if it ever finishes a call-return
    // withing the same transition
    boolean recorded;
    String reasonForInterruption;

    int readCount;
    int writeCount;
    int instructionCount;

    int argsMatchCount;
    int attemptedMatchCount;
    int failedMatchCount;
}