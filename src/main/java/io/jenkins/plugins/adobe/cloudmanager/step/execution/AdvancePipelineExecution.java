package io.jenkins.plugins.adobe.cloudmanager.step.execution;

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

import java.util.List;

import hudson.AbortException;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Execution for a {@link io.jenkins.plugins.adobe.cloudmanager.step.AdvancePipelineStep}, advancing the remote Cloud Manager pipeline.
 */
public class AdvancePipelineExecution extends AbstractStepExecution {

  private final List<StepAction> actions;

  public AdvancePipelineExecution(StepContext context, List<StepAction> actions) {
    super(context);
    this.actions = actions;
  }

  @Override
  public void doStart() throws Exception {
    try {
      CloudManagerPipelineExecution build = getBuildData().getCmExecution();
      CloudManagerApi api = getApi();
      PipelineExecution pe = api.getExecution(build.getProgramId(), build.getPipelineId(), build.getExecutionId());

      PipelineExecutionStepState step = api.getCurrentStep(pe);
      StepAction stepAction = StepAction.valueOf(step.getAction());
      if (actions.contains(stepAction)) {
        getTaskListener().getLogger().println(Messages.AdvancePipelineExecution_info_advancingPipeline(stepAction));
        api.advanceExecution(pe);
        getContext().onSuccess(null);
      } else {
        throw new AbortException(Messages.AdvancePipelineExecution_error_invalidPipelineState(stepAction));
      }
    } catch (CloudManagerApiException e) {
      throw new AbortException(e.getLocalizedMessage());
    }
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
