package io.jenkins.plugins.adobe.cloudmanager.step;

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
 * Action for Pipeline Step State Executions which need to be paused and display info.
 * <p>
 * Executions could be put into parallel blocks, so have to handle this.
 * A lot of this is modeled off of {@code InputAction}
 */
public class PipelineStepStateAction implements RunAction2, Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStepStateAction.class);
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
    return Messages.PipelineStepStateAction_displayName();
  }

  @Override
  public String getUrlName() {
    return "acm-pipeline-step-state";
  }

  @Override
  public void onAttached(Run<?, ?> run) {
    this.run = run;
  }

  @Override
  public void onLoad(Run<?, ?> run) {
    this.run = run;
  }

  public synchronized void add(@Nonnull PipelineStepStateExecution step) throws IOException, InterruptedException, TimeoutException {
    loadExecutions();
    if (executions == null) {
      throw new IOException(Messages.PipelineStepStateAction_error_loadState());
    }
    this.executions.add(step);
    ids.add(step.getId());
    run.save();
  }

  public synchronized PipelineStepStateExecution getExecution(String id) throws InterruptedException, TimeoutException {
    loadExecutions();
    if (executions == null) {
      return null;
    }
    return executions.stream().filter(e -> StringUtils.equals(id, e.getId())).findFirst().orElse(null);
  }

  public synchronized List<PipelineStepStateExecution> getExecutions() throws InterruptedException, TimeoutException {
    loadExecutions();
    return (executions == null) ? Collections.emptyList() : new ArrayList<>(executions);
  }

  public synchronized void remove(@Nonnull PipelineStepStateExecution step) throws IOException, InterruptedException, TimeoutException {
    loadExecutions();
    if (executions == null) {
      throw new IOException(Messages.PipelineStepStateAction_error_loadState());
    }
    this.executions.remove(step);
    ids.remove(step.getId());
    run.save();
  }

  /**
   * For URL Access
   */
  public PipelineStepStateExecution getDynamic(String token) throws InterruptedException, TimeoutException {
    return getExecution(token);
  }

  private synchronized void loadExecutions() throws InterruptedException, TimeoutException {
    if (executions == null) { // Loaded after restart.
      try {
        Optional<FlowExecution> optional = StreamSupport.stream(FlowExecutionList.get().spliterator(), false).filter((ex) -> {
          try {
            return ex.getOwner().getExecutable() == run;
          } catch (IOException e) {
            LOGGER.error(Messages.PipelineStepStateAction_error_loadExecutions(e.getLocalizedMessage()));
          }
          return false;
        }).findFirst();
        if (optional.isPresent()) {
          FlowExecution execution = optional.get();
          List<StepExecution> candidates = execution.getCurrentExecutions(true).get(60, TimeUnit.SECONDS);
          executions = candidates.stream()
              .filter(se -> se instanceof PipelineStepStateExecution && ids.contains(((PipelineStepStateExecution) se).getId()))
              .map((se) -> ((PipelineStepStateExecution) se))
              .collect(Collectors.toList());
          if (executions.size() < ids.size()) {
            LOGGER.warn(Messages.PipelineStepStateAction_warn_lostExecutions(run));
          }
        } else {
          LOGGER.warn(Messages.PipelineStepStateAction_warn_missingExecution(run));
        }
      } catch (InterruptedException | TimeoutException e) {
        throw e;
      } catch (Exception e) {
        LOGGER.error(Messages.PipelineStepStateAction_error_loadExecutions(e.getLocalizedMessage()));
      }
    }
  }
}
