package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.util.Objects;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import hudson.AbortException;
import hudson.model.Run;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

public abstract class AbstractStepExecution extends StepExecution {

  private CloudManagerBuildAction data;

  public AbstractStepExecution(StepContext context) {
    super(context);
  }

  protected CloudManagerBuildAction getBuildData() {
    return data;
  }

  protected void validateData() throws AbortException {
    if (data == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
    AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(data.getAioProjectName());
    // Make sure Build Data is populated - when resuming.
    if (aioProject == null ||
        StringUtils.isBlank(data.getProgramId()) ||
        StringUtils.isBlank(data.getPipelineId()) ||
        StringUtils.isBlank(data.getExecutionId())) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
  }

  @Nonnull
  protected AdobeIOProjectConfig getAioProject() throws AbortException {
    AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(data.getAioProjectName());
    // Restart may be after AIO Project is removed.
    if (aioProject == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
    return aioProject;
  }

  @Nonnull
  protected Secret getAccessToken() throws AbortException {
    Secret token = getAioProject().authenticate();
    if (token == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_authentication());
    }
    return token;
  }

  @Nonnull
  protected CloudManagerApi getApi() throws AbortException {
    AdobeIOProjectConfig aioProject = getAioProject();
    Secret token = getAccessToken();
    return CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText(), aioProject.getApiUrl());
  }

  @Override
  public boolean start() throws Exception {
    Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
    data = run.getAction(CloudManagerBuildAction.class);
    if (data == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
    return doStart();
  }

  @Override
  public void onResume() {
    try {
      validateData();
    } catch (AbortException e) {
      getContext().onFailure(e);
    }
  }

  public abstract boolean doStart() throws Exception;
}
