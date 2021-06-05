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
import java.util.Set;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * {@link Step} which waits for a Cloud Manager pipeline end event.
 * <p>
 *   If an Pipeline end event is received, all inner {@link PipelineStepStateStep} instances are quietly ended.
 *   Any other inner steps are allowed to complete as configured.
 * </p>
 * <p>
 *   See the <a href="https://www.adobe.io/apis/experiencecloud/cloud-manager/api-reference.html#!AdobeDocs/cloudmanager-api-docs/master/swagger-specs/events.yaml">Cloud Manager Events</a> documentation.
 * </p>
 * <p>
 *   <strong>Note:</strong> Syntax wise, until <a href="https://issues.jenkins.io/browse/JENKINS-65646">JENKINS-65646</a> is resolved, a block must be specified, even if its empty.
 * </p>
 */
public class PipelineEndStep extends Step {

  private boolean mirror = true;
  private boolean empty = false;

  @DataBoundConstructor
  public PipelineEndStep() {

  }

  /**
   * Flag if this step mirror the remote state. (Remote aborted results in local failure.)
   */
  public boolean isMirror() {
    return mirror;
  }

  @DataBoundSetter
  public void setMirror(boolean mirror) {
    this.mirror = mirror;
  }

  public boolean isEmpty() { return empty; }

  @DataBoundSetter
  public void setEmpty(boolean empty) { this.empty = empty; }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new PipelineEndExecution(context, mirror, empty);
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getFunctionName() {
      return "acmPipelineEnd";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, FlowNode.class, TaskListener.class)));
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.PipelineEndStep_displayName();
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }
  }
}
