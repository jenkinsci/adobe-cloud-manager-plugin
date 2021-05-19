package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import hudson.AbortException;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Helper Step for interacting with Cloud Manager.
 * <p>
 *   Standard functions used by all steps to ensure standard capabilities.
 * </p>
 */
public abstract class AbstractStepExecution extends StepExecution {

  private final String id;

  public AbstractStepExecution(StepContext context) {
    super(context);
    id = UUID.randomUUID().toString();
  }

  /**
   * Get the Cloud Manager Build info from the run.
    */
  @CheckForNull
  protected CloudManagerBuildAction getBuildData() throws IOException, InterruptedException {
    return getRun().getAction(CloudManagerBuildAction.class);
  }

  @Nonnull
  public String getId() {
    return id;
  }

  /**
   * Validate that the data exists for the current run, any missing information will fail the run.
   */
  protected void validateData() throws IOException, InterruptedException {
    CloudManagerBuildAction data = getBuildData();
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

  /**
   * Retrieve the configured Adobe IO Project configured based on the information configured in the Run.
   */
  @Nonnull
  protected AdobeIOProjectConfig getAioProject() throws IOException, InterruptedException {
    AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(getBuildData().getAioProjectName());
    // Restart may be after AIO Project is removed.
    if (aioProject == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
    return aioProject;
  }

  /**
   * Authenticate to Adobe IO and get a valid token, for API potential API requests.
   */
  @Nonnull
  protected Secret getAccessToken() throws IOException, InterruptedException {
    Secret token = getAioProject().authenticate();
    if (token == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_authentication());
    }
    return token;
  }

  /**
   * Build a Cloud Manager API based on the configured Adobe IO Project.
   */
  @Nonnull
  protected CloudManagerApi getApi() throws IOException, InterruptedException {
    AdobeIOProjectConfig aioProject = getAioProject();
    Secret token = getAccessToken();
    return CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText());
  }

  /**
   * Helper to get the Run from the context.
   */
  @Nonnull
  protected Run<?, ?> getRun() throws IOException, InterruptedException {
    return Objects.requireNonNull(getContext().get(Run.class));
  }

  /**
   * Helper to get the TaskListener from the context.
   */
  @Nonnull
  protected TaskListener getTaskListener() throws IOException, InterruptedException {
    return Objects.requireNonNull(getContext().get(TaskListener.class));
  }

  /**
   * Helper to get the FlowNode from the context.
   */
  @Nonnull
  protected FlowNode getFlowNode() throws IOException, InterruptedException {
    return Objects.requireNonNull(getContext().get(FlowNode.class));
  }

  /**
   * Starts the Execution.
   */
  @Override
  public final boolean start() throws Exception {
    if (getBuildData() == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
    doStart();
    return false;
  }

  /**
   * Restart the Execution after a restart.
   */
  @Override
  public final void onResume() {
    try {
      validateData();
      doResume();
    } catch (IOException | InterruptedException e) {
      getContext().onFailure(e);
    }
  }

  /**
   * Stop the Execution when an error occurs.
   */
  @Override
  public final void stop(@Nonnull Throwable cause) throws Exception {
    doStop();
    getContext().onFailure(cause);
  }

  /**
   * Subclasses should override this to provide specific logic on step start.
   */
  public void doStart() throws Exception { }

  /**
   * Subclasses should override this to provide specific logic after a restart operation.
   */
  public void doResume() throws IOException, InterruptedException {}

  /**
   * Subclasses should override this to require specific logic during a stop operation.
   */
  public void doStop() throws Exception {}
}
