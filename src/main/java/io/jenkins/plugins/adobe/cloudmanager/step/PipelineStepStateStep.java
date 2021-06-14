package io.jenkins.plugins.adobe.cloudmanager.step;

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
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * {@link Step} which waits for a Cloud Manager pipeline step event.
 * <p>
 *   <strong>Occurrence</strong> events will simply log a message.
 * </p>
 * <p>
 *   <strong>Waiting</strong> events will force the pipeline to pause for user inputs.
 *   All waiting events only support approval or cancel options.
 *   User selection will invoke the associated operation in Cloud Manager.
 * </p>
 * <p>
 *   See the <a href="https://www.adobe.io/apis/experiencecloud/cloud-manager/api-reference.html#!AdobeDocs/cloudmanager-api-docs/master/swagger-specs/events.yaml">Cloud Manager Events</a> documentation.
 * </p>
 */
public class PipelineStepStateStep extends Step {

  private List<StepAction> actions;
  private boolean autoApprove = false;
  private boolean advance = true;
  private boolean waitingPause = true;

  @DataBoundConstructor
  public PipelineStepStateStep() {

  }

  /**
   * List of actions to which this Step will respond. <strong>Default:</strong> all events.
   */
  @Nonnull
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

  /**
   * Flag to indicate if this step should auto-approve if it receives a {@code waiting} event.
   */
  public boolean isAutoApprove() {
    return autoApprove;
  }

  @DataBoundSetter
  public void setAutoApprove(boolean autoApprove) {
    this.autoApprove = autoApprove;
  }

  /**
   * Flag to indicate if this step should auto-advance when it receives a {@code finished} event.
   */
  public boolean isAdvance() {
    return advance;
  }

  @DataBoundSetter
  public void setAdvance(boolean advance) {
    this.advance = advance;
  }

  /**
   * Flag to indicate if the pipeline should pause on a <strong>waiting</strong> event.
   */
  public boolean isWaitingPause() { return this.waitingPause; }

  @DataBoundSetter
  public void setWaitingPause(boolean waitingPause) { this.waitingPause = waitingPause; }

  /**
   * List all actions for the UI generator example.
   */
  @Nonnull
  public StepAction[] listActions() {
    return StepAction.values();
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    if (autoApprove && !waitingPause) {
      throw new AbortException(Messages.PipelineStepStateStep_failure_approveAndNoWait());
    }
    return new PipelineStepStateExecution(context, new HashSet<>(getActions()), autoApprove, advance, waitingPause);
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
