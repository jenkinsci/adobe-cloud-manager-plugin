package io.jenkins.plugins.adobe.cloudmanager.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.AdvancePipelineExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * {@link Step} which will advance the current Cloud Manager pipeline execution in Cloud Manager Build context
 *
 * By default it will advance the Pipeline regardless of the current state. To narrow the scope, specify the {@code action} context.
 */
public class AdvancePipelineStep extends Step {
  private static final List<StepAction> ALLOWED_ACTIONS = Arrays.asList(StepAction.codeQuality, StepAction.approval);

  private List<StepAction> actions = new ArrayList<>(ALLOWED_ACTIONS);;

  @DataBoundConstructor
  public AdvancePipelineStep() {

  }

  /**
   * List of actions to which this Step will respond. <strong>Default:</strong> {@code codeQuality} and {@code approval} actions.
   */
  @Nonnull
  public List<StepAction> getActions() {
    return actions;
  }

  @DataBoundSetter
  public void setActions(List<StepAction> actions) {
    if (actions != null && !actions.isEmpty()) {
      this.actions = actions;
    }
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    for (StepAction action : actions) {
      if (!ALLOWED_ACTIONS.contains(action)) {
        throw new AbortException(Messages.ApprovePipelineStep_error_invalidAction(action));
      }
    }
    return new AdvancePipelineExecution(context, actions);
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {
    @Override
    public String getFunctionName() {
      return "acmAdvancePipeline";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, TaskListener.class)));
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.ApprovePipelineStep_displayName();
    }
  }
}
