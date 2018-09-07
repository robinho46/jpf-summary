package gov.nasa.jpf;

class MethodCounter {
    public MethodCounter(String methodName) {
        this.methodName = methodName;
        totalCalls = 0;
        runningAlone = 0;
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
        //str += ",\"readCount\":"+readCount;
        //str += ",\"writeCount\":"+writeCount;
        str += ",\"instructionCount\":" + instructionCount;
        str += ",\"recorded\":" + recorded;
        str += ",\"interruption\":\"" + reasonForInterruption + "\"";
        str += "}";
        return str;
    }

    public boolean isRecorded() {
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

    public int totalCalls;

    // the number of times the method is called
    // when there's only a single thread.
    private int runningAlone;

    // true if it ever finishes a call-return
    // withing the same transition
    public boolean recorded;
    public String reasonForInterruption;

    public int readCount;
    public int writeCount;
    public int instructionCount;

    public int argsMatchCount;
    public int attemptedMatchCount;
    public int failedMatchCount;
}