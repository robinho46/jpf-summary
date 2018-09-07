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
package gov.nasa.jpf.listener;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.ThreadInfo;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.Duration;

/**
  * Small Search listener that tries to measure the execution time of JPF
  * a bit more accurately (without the overhead of loading extensions etc)
  * also terminates the search after a given number of minutes.
  */

class SearchTimer extends ListenerAdapter {
  private PrintWriter out;
  private Instant start;
  private int timeLimit;
  public SearchTimer (Config config, JPF jpf) {
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
  public void instructionExecuted (VM vm, ThreadInfo ti, Instruction nextInsn, Instruction executedInsn) {
    if(timeLimit < 0) {
      return;
    }

    if(Duration.between(start, Instant.now()).toMinutes() >= timeLimit ) {
      vm.getSearch().terminate();
    }
  }

  @Override
  public void searchFinished(Search search) {
    Instant end = Instant.now();
    out.println("{Time:" + Duration.between(start,end).toMillis() + "}");
  }

}
