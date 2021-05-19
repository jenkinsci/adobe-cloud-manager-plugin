package io.jenkins.plugins.adobe.cloudmanager.action;

/*-
 * #%L
 * Adobe Cloud Manager Plugin
 * %%
 * Copyright (C) 2020 - 2021 Adobe Inc.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.model.Run;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Action for Pipeline Executions which need to be paused and display info.
 * <p>
 * Executions could be put into parallel blocks, so have to handle this. Also Jenkins could shut down while waiting.
 * A lot of this is modeled off of {@code InputAction}
 * </p>
 */
public class PipelineWaitingAction implements RunAction2, Serializable {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineWaitingAction.class);
  private static final long serialVersionUID = 1L;

  private transient List<PipelineStepStateExecution> executions = new ArrayList<>();
  private transient Run<?, ?> run;
  private List<String> ids = new CopyOnWriteArrayList<>();

  @Override
  public String getIconFileName() {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    return jenkins.model.Jenkins.RESOURCE_PATH + "/plugin/adobe-cloud-manager/icons/Adobe_Experience_Cloud_logo_48px.png";
  }

  @Override
  public String getDisplayName() {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    return Messages.PipelineWaitingAction_displayName();
  }

  @Override
  public String getUrlName() {
    return "acm-pipeline";
  }

  public Run<?, ?> getRun() {
    return run;
  }

  @Override
  public void onAttached(Run<?, ?> run) {
    this.run = run;
  }

  @Override
  public void onLoad(Run<?, ?> run) {
    this.run = run;
    synchronized (this) {
      if (ids == null) {
        assert executions != null && !executions.contains(null) : executions;
        ids = new CopyOnWriteArrayList<>();
        executions.stream().map(e -> ids.add(e.getId())).collect(Collectors.toList());
        executions = null;
      }
    }
  }

  /**
   * Add the step to the list of executions, for later reloading.
   */
  public synchronized void add(@Nonnull PipelineStepStateExecution step) throws IOException, InterruptedException, TimeoutException {
    loadExecutions();
    if (executions == null) {
      throw new IOException(Messages.PipelineWaitingAction_error_loadState());
    }
    this.executions.add(step);
    ids.add(step.getId());
    run.save();
  }

  /**
   * Returns the execution based on the id.
   */
  public synchronized PipelineStepStateExecution getExecution(@Nonnull String id) throws InterruptedException, TimeoutException {
    loadExecutions();
    if (executions == null) {
      return null;
    }
    return executions.stream().filter(e -> StringUtils.equals(id, e.getId())).findFirst().orElse(null);
  }

  /**
   * Lists all the stored executions. Used by the UI for form display/submission.
   */
  public synchronized List<PipelineStepStateExecution> getExecutions() throws InterruptedException, TimeoutException {
    loadExecutions();
    return (executions == null) ? Collections.emptyList() : new ArrayList<>(executions);
  }

  /**
   * Remove the specified step from the list of known executions.
   */
  public synchronized void remove(@Nonnull PipelineStepStateExecution step) throws IOException, InterruptedException, TimeoutException {
    loadExecutions();
    if (executions == null) {
      throw new IOException(Messages.PipelineWaitingAction_error_loadState());
    }
    executions.remove(step);
    ids.remove(step.getId());
    run.save();
  }

  /**
   * Used by the UI for iterating over the executions and displaying.
   */
  public PipelineStepStateExecution getDynamic(String id) throws InterruptedException, TimeoutException {
    return getExecution(id);
  }

  // Load all of the executions. Be careful with the logic here, can sometimes block the VM thread - don't want that.
  private synchronized void loadExecutions() throws InterruptedException, TimeoutException {
    if (executions == null) { // Loaded after restart.
      try {
        Optional<FlowExecution> execution = StreamSupport.stream(FlowExecutionList.get().spliterator(), false).filter((ex) -> {
          try {
            return ex.getOwner().getExecutable() == run;
          } catch (IOException e) {
            LOGGER.error(Messages.PipelineWaitingAction_error_loadExecutions(e.getLocalizedMessage()));
          }
          return false;
        }).findFirst();
        if (execution.isPresent()) {
          List<StepExecution> candidates = execution.get().getCurrentExecutions(true).get(60, TimeUnit.SECONDS);
          executions = candidates.stream()
              .filter(se -> se instanceof PipelineStepStateExecution && ids.contains(((PipelineStepStateExecution) se).getId()))
              .map((se) -> ((PipelineStepStateExecution) se))
              .collect(Collectors.toList());
          if (executions.size() < ids.size()) {
            LOGGER.warn(Messages.PipelineWaitingAction_warn_lostExecutions(run));
          }
        } else {
          LOGGER.warn(Messages.PipelineWaitingAction_warn_missingExecution(run));
        }
      } catch (InterruptedException | TimeoutException e) {
        throw e;
      } catch (Exception e) {
        LOGGER.error(Messages.PipelineWaitingAction_error_loadExecutions(e.getLocalizedMessage()));
      }
    }
  }
}
