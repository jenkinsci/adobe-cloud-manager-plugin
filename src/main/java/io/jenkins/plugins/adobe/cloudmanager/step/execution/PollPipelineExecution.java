package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class PollPipelineExecution extends AbstractStepExecution {

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
        Secret token = getAccessToken();
        if (checkExecution(aioProject, token)) {
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

  private boolean checkExecution(AdobeIOProjectConfig aioProject, Secret token) throws AbortException {
    try {
      CloudManagerApi api = CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText());
      if (api.isExecutionRunning(getBuildData().getProgramId(), getBuildData().getPipelineId(), getBuildData().getExecutionId())) {
        if (!quiet) {
          getContext().get(TaskListener.class).getLogger().println(Messages.PollPipelineExecution_waiting(Util.getTimeSpanString(recurrencePeriod)));
        }
        return false;
      }
      getContext().get(TaskListener.class).getLogger().println(Messages.PollPipelineExecution_complete());
    } catch (CloudManagerApiException e) {
      throw new AbortException(Messages.PollPipelineExecution_error_CloudManagerApiException(e.getLocalizedMessage()));
    } catch (Exception e) {
      throw new AbortException(e.getLocalizedMessage());
    }
    return true;
  }

}
