package gov.nasa.jpf;

import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

import java.util.HashSet;

public class RecordingListener extends ListenerAdapter {
    protected static CounterContainer counterContainer = new CounterContainer();

    // contains the names of the methods that should never be recorded
    // this could be because they are interrupted by a transition
    // or because they call native methods that we can't track
    protected static HashSet<String> blackList = new HashSet<>();

    // contains the names of the methods that have been recorded as
    // doing a complete call-return cycle within a single transition
    protected static HashSet<String> recorded = new HashSet<>();
    // contains the names of the methods currently being recorded
    protected static HashSet<String> recording = new HashSet<>();

    void stopRecording() {
        for (String methodName : recording) {
            assert (!recorded.contains(methodName));
            // not conditional, as these interruptions will  override any others
            counterContainer.overrideReasonForInterruption(methodName);
            blackList.add(methodName);
        }

        recording = new HashSet<>();
    }

    @Override
    public void threadInterrupted(VM vm, ThreadInfo interruptedThread) {
        stopRecording();
    }

    @Override
    public void objectLocked(VM vm, ThreadInfo currentThread, ElementInfo lockedObject) {
        stopRecording();
    }

    @Override
    public void objectUnlocked(VM vm, ThreadInfo currentThread, ElementInfo unlockedObject) {
        stopRecording();
    }

    @Override
    public void objectWait(VM vm, ThreadInfo currentThread, ElementInfo waitingObject) {
        stopRecording();
    }

    @Override
    public void objectNotify(VM vm, ThreadInfo currentThread, ElementInfo notifyingObject) {
        stopRecording();
    }

    @Override
    public void objectNotifyAll(VM vm, ThreadInfo currentThread, ElementInfo notifyingObject) {
        stopRecording();
    }

    @Override
    public void objectExposed(VM vm, ThreadInfo currentThread, ElementInfo fieldOwnerObject, ElementInfo exposedObject) {
        stopRecording();
    }

    @Override
    public void objectShared(VM vm, ThreadInfo currentThread, ElementInfo sharedObject) {
        stopRecording();
    }

    @Override
    public void choiceGeneratorRegistered(VM vm, ChoiceGenerator<?> nextCG, ThreadInfo currentThread, Instruction executedInstruction) {
        stopRecording();
    }

    @Override
    public void choiceGeneratorSet(VM vm, ChoiceGenerator<?> newCG) {
        stopRecording();
    }

    @Override
    public void choiceGeneratorAdvanced(VM vm, ChoiceGenerator<?> currentCG) {
        stopRecording();
    }

    @Override
    public void stateAdvanced(Search search) {
        stopRecording();
    }

    @Override
    public void stateBacktracked(Search search) {
        stopRecording();
    }

}
