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
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PollPipelineExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Polls the Cloud Manager API to determine if the pipeline has completed.
 * <p>
 *   The pipeline execution needs to be in the Job/Build context.
 * </p>
 */
public class PollPipelineStep extends Step {

  /**
   * Minimum time to wait before poll checks.
   */
  static final long MIN_RECURRENCE_PERIOD = 30000; // 30 seconds
  /**
   * Maximum time to wait before poll checks.
   */
  static final long MAX_RECURRENCE_PERIOD = 900000; // 15 minutes
  /**
   * Default time to wait before poll checks.
   */
  static final long DEFAULT_RECURRENCE_PERIOD = 300000; // 5 minutes

  private long recurrencePeriod = DEFAULT_RECURRENCE_PERIOD;
  private boolean quiet = false;

  @DataBoundConstructor
  public PollPipelineStep() {
  }

  /**
   * Wait period before repeated API checks, in milliseconds
   */
  public long getRecurrencePeriod() {
    return recurrencePeriod;
  }

  @DataBoundSetter
  public void setRecurrencePeriod(long recurrencePeriod) {
    this.recurrencePeriod = Math.max(MIN_RECURRENCE_PERIOD, Math.min(recurrencePeriod, MAX_RECURRENCE_PERIOD));
  }

  /**
   * Flag to indicate whether or not this step should log each API check.
   */
  public boolean isQuiet() {
    return quiet;
  }

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
