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

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import hudson.AbortException;
import hudson.Util;
import hudson.model.TaskListener;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Execution for a {@link io.jenkins.plugins.adobe.cloudmanager.step.PollPipelineStep}.
 * Periodically checks if the specified Cloud Manager execution is complete.
 */
public class PollPipelineExecution extends AbstractStepExecution {

  private static final long serialVersionUID = 1L;

  private final long recurrencePeriod;
  private final boolean quiet;
  protected transient volatile ScheduledFuture<?> task;

  public PollPipelineExecution(StepContext context, long recurrencePeriod, boolean quiet) {
    super(context);
    this.recurrencePeriod = recurrencePeriod;
    this.quiet = quiet;
  }

  @Override
  public void doStart() throws Exception {
    createTask();
  }

  @Override
  public void doResume() {
    createTask();
  }

  @Override
  public void doStop() throws Exception {
    if (task != null) {
      task.cancel(true);
      task = null;
    }
  }

  protected void createTask() {
    task = Timer.get().scheduleWithFixedDelay(() -> {
      try {
        AdobeIOProjectConfig aioProject = getAioProject();
        if (checkExecution(aioProject.getName())) {
          task.cancel(true);
          task = null;
          getContext().onSuccess(null);
        }
      } catch (IOException | InterruptedException e) {
        task.cancel(true);
        task = null;
        getContext().onFailure(e);
      }
    }, 0, recurrencePeriod, TimeUnit.MILLISECONDS);
  }

  private boolean checkExecution(String aioProjectName) throws AbortException {
    try {
      CloudManagerApi api = CloudManagerApiUtil.createApi().apply(aioProjectName).orElseThrow(() -> new AbortException(Messages.AbstractStepExecution_error_missingBuildData()));
      CloudManagerPipelineExecution execution = getBuildData().getCmExecution();
      if (api.isExecutionRunning(execution.getProgramId(), execution.getPipelineId(), execution.getExecutionId())) {
        if (!quiet) {
          getContext().get(TaskListener.class).getLogger().println(Messages.PollPipelineExecution_waiting(Util.getTimeSpanString(recurrencePeriod)));
        }
        return false;
      }
      getContext().get(TaskListener.class).getLogger().println(Messages.PollPipelineExecution_complete());
    } catch (AbortException e) {
      throw e;
    } catch (CloudManagerApiException e) {
      throw new AbortException(Messages.PollPipelineExecution_error_CloudManagerApiException(e.getLocalizedMessage()));
    } catch (Exception e) {
      throw new AbortException(e.getLocalizedMessage());
    }
    return true;
  }

}
