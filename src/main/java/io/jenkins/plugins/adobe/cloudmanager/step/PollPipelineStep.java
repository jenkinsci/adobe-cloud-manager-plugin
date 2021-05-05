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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerBuildData;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
    return new StepExecutionImpl(context, recurrencePeriod, quiet);
  }

  public static final class StepExecutionImpl extends StepExecution {

    private static final long serialVersionUID = 1;
    private final long recurrencePeriod;
    private final boolean quiet;
    private transient volatile ScheduledFuture<?> task;
    private CloudManagerBuildData data;

    StepExecutionImpl(StepContext context, long recurrencePeriod, boolean quiet) {
      super(context);
      this.recurrencePeriod = recurrencePeriod;
      this.quiet = quiet;
    }

    @Override
    public boolean start() throws Exception {
      Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
      data = run.getAction(CloudManagerBuildData.class);
      if (data == null) {
        throw new AbortException(Messages.PollPipelineStep_error_missingBuildData());
      }
      setupCheck();
      return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
      if (task != null) {
        task.cancel(true);
      }
      super.stop(cause);
    }

    @Override
    public void onResume() {
      setupCheck();
    }

    private void setupCheck() {
      getContext().saveState();
      task = Timer.get().scheduleWithFixedDelay(() -> {
        try {
          AdobeIOProjectConfig aioProject = getAioProject();
          Secret token = getAccessToken(aioProject);
          if (checkExecution(aioProject, token)) {
            getContext().onSuccess(null);
            task.cancel(true);
            task = null;
          }
        } catch (AbortException e) {
          getContext().onFailure(e);
          task.cancel(true);
          task = null;
        }
      }, 0, recurrencePeriod, TimeUnit.MILLISECONDS);
    }

    @Nonnull
    private AdobeIOProjectConfig getAioProject() throws AbortException {
      AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(data.getAioProjectName());
      // Restart may be after AIO Project is removed.
      if (aioProject == null) {
        throw new AbortException(Messages.PollPipelineStep_error_missingBuildData());
      }
      return aioProject;
    }

    @Nonnull
    private Secret getAccessToken(AdobeIOProjectConfig aioProject) throws AbortException {
      Secret token = aioProject.authenticate();
      if (token == null) {
        throw new AbortException(Messages.PollPipelineStep_error_authentication());
      }
      return token;
    }

    private boolean checkExecution(AdobeIOProjectConfig aioProject, Secret token) throws AbortException {
      try {
        CloudManagerApi api = CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText());
        if (api.isExecutionRunning(data.getProgramId(), data.getPipelineId(), data.getExecutionId())) {
          if (!quiet) {
            getContext().get(TaskListener.class).getLogger().println(Messages.PollPipelineStep_waiting(Util.getTimeSpanString(recurrencePeriod)));
          }
          return false;
        }
      } catch (CloudManagerApiException e) {
        throw new AbortException(Messages.PollPipelineStep_error_CloudManagerApiException(e.getLocalizedMessage()));
      } catch (Exception e) {
        throw new AbortException(e.getLocalizedMessage());
      }
      return true;
    }
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
      return ImmutableSet.of(Run.class, TaskListener.class);
    }
  }
}
