package io.jenkins.plugins.adobe.cloudmanager.step;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class PipelineStepStateStep extends Step {

  private List<StepAction> actions;
  private boolean showLogs = true;
  private boolean autoAdvance = false;

  @DataBoundConstructor
  public PipelineStepStateStep() {

  }

  public List<StepAction> getActions() {
    if (actions == null) {
      actions = Arrays.asList(StepAction.values());
    }
    return actions;
  }

  @DataBoundSetter
  public void setActions(List<StepAction> actions) {
    this.actions = actions;
  }

  public boolean isShowLogs() {
    return showLogs;
  }

  @DataBoundSetter
  public void setShowLogs(boolean showLogs) {
    this.showLogs = showLogs;
  }

  public boolean isAutoAdvance() {
    return autoAdvance;
  }

  @DataBoundSetter
  public void setAutoAdvance(boolean autoAdvance) {
    this.autoAdvance = autoAdvance;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new PipelineStepStateExecution(context, new HashSet<>(getActions()));
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getFunctionName() {
      return "acmPipelineStepState";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.PipelineStepStateStep_displayName();
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, FlowNode.class, TaskListener.class)));
    }
  }
}
