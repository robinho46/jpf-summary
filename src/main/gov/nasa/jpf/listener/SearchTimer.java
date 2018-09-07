package gov.nasa.jpf.listener;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;

/**
 * Small Search listener that tries to measure the execution time of JPF
 * a bit more accurately (without the overhead of loading extensions etc)
 * also terminates the search after a given number of minutes.
 */

class SearchTimer extends ListenerAdapter {
    private PrintWriter out;
    private Instant start;
    private int timeLimit;

    public SearchTimer(Config config, JPF jpf) {
        out = new PrintWriter(System.out, true);
        timeLimit = config.getInt("search.timeout", -1);
    }

    @Override
    public void searchStarted(Search search) {
        start = Instant.now();
    }

    @Override
    public void stateAdvanced(Search search) {
    }

    @Override
    public void instructionExecuted(VM vm, ThreadInfo ti, Instruction nextInsn, Instruction executedInsn) {
        if (timeLimit < 0) {
            return;
        }

        if (Duration.between(start, Instant.now()).toMinutes() >= timeLimit) {
            vm.getSearch().terminate();
        }
    }

    @Override
    public void searchFinished(Search search) {
        Instant end = Instant.now();
        out.println("{Time:" + Duration.between(start, end).toMillis() + "}");
    }

}
