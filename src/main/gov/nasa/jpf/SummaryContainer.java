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
package gov.nasa.jpf;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import gov.nasa.jpf.vm.ElementInfo;


/**
  * Helper class that provides the ability to store multiple summaries for a single method.
  */
public class SummaryContainer {
  private Map<String,List<MethodSummary>> container;
  // the maximum number of contexts which we capture
  private static final int CAPACITY = 50;
  public SummaryContainer() {
    container = new HashMap<>();
  }

  public void addMethod(String methodName) {
    List<MethodSummary> summaries = container.get(methodName);
    if(summaries == null) {
      return;
    }
  }

  public void addSummary(String methodName, MethodContext context, MethodModifications mods) {
    List<MethodSummary> summaries = container.get(methodName);
    if(summaries == null) {
      summaries = new ArrayList<>();
      summaries.add(new MethodSummary(context,mods));
      container.put(methodName, summaries);
      return;
    }
    if(summaries.size() < CAPACITY) {
      summaries.add(new MethodSummary(context, mods));
      return;
    }

    //throw new IndexOutOfBoundsException("Trying to add too many summaries for " + methodName);
  }

  public boolean canStoreMoreSummaries(String methodName) {
    List<MethodSummary> summaries = container.get(methodName);
    return summaries == null || summaries.size() < CAPACITY; 
  }

  public boolean hasSummary(String methodName) {
    List<MethodSummary> summaries = container.get(methodName);
    return summaries != null && summaries.size() > 0; 
  }

  public MethodSummary hasMatchingContext(String methodName, ElementInfo _this, Object[] args, boolean runningAlone) {
    List<MethodSummary> summaries = container.get(methodName);
    if(summaries == null) {
      return null;
    }

    for(MethodSummary summary : summaries) {
      if(summary.context.match(_this, args, runningAlone)) {
        return summary;
      }
    }
    return null;
  }

  public MethodSummary hasMatchingContext(String methodName, Object[] args, boolean runningAlone) {
    List<MethodSummary> summaries = container.get(methodName);
    if(summaries == null) {
      return null;
    }

    for(MethodSummary summary : summaries) {

      if(summary.context.match(args, runningAlone)) {
        return summary;
      }
    }
    return null;
  }
}