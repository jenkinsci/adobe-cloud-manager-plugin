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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.AbstractStepExecution;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PollPipelineExecution;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Polls for a Cloud Manager pipeline's completion. The pipeline execution needs to be in the Job/Build context.
 */
public class PollPipelineStep extends Step {

  static final long MIN_RECURRENCE_PERIOD = 30000; // 30 seconds
  static final long MAX_RECURRENCE_PERIOD = 900000; // 15 minutes
  static final long DEFAULT_RECURRENCE_PERIOD = 300000; // 5 minutes

  private long recurrencePeriod = DEFAULT_RECURRENCE_PERIOD;
  private boolean quiet = false;

  @DataBoundConstructor
  public PollPipelineStep() {
  }

  public long getRecurrencePeriod() {
    return recurrencePeriod;
  }

  /**
   * Wait period before repeated API checks, in milliseconds
   *
   * @param recurrencePeriod wait period in milliseconds
   */
  @DataBoundSetter
  public void setRecurrencePeriod(long recurrencePeriod) {
    this.recurrencePeriod = Math.max(MIN_RECURRENCE_PERIOD, Math.min(recurrencePeriod, MAX_RECURRENCE_PERIOD));
  }

  public boolean isQuiet() {
    return quiet;
  }

  /**
   * Whether or not to log between checks.
   *
   * @param quiet quiet flag
   */
  @DataBoundSetter
  public void setQuiet(boolean quiet) {
    this.quiet = quiet;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new PollPipelineExecution(context, recurrencePeriod, quiet);
  }


  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getFunctionName() {
      return "acmPollPipeline";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.PollPipelineStep_displayName();
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, TaskListener.class)));
    }
  }
}
