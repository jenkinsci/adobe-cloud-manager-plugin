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
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
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
  @Nonnull
  protected CloudManagerBuildAction getBuildData() throws IOException, InterruptedException {
    CloudManagerBuildAction data = getRun().getAction(CloudManagerBuildAction.class);
    if (data == null) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
    return data;
  }

  @Nonnull
  public String getId() {
    return id;
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
   * Validate that the data exists for the current run, any missing information will fail the run.
   */
  protected void validateData() throws IOException, InterruptedException {
    getAioProject();
    CloudManagerBuildAction data = getBuildData();
    // Make sure Build Data is populated - when resuming.
    if (StringUtils.isBlank(data.getProgramId()) ||
        StringUtils.isBlank(data.getPipelineId()) ||
        StringUtils.isBlank(data.getExecutionId())) {
      throw new AbortException(Messages.AbstractStepExecution_error_missingBuildData());
    }
  }

  /**
   * Build a Cloud Manager API based on the configured Adobe IO Project.
   */
  @Nonnull
  protected CloudManagerApi getApi() throws IOException, InterruptedException {
    return CloudManagerApiUtil.createApi().apply(getBuildData().getAioProjectName()).orElseThrow(() -> new AbortException(Messages.AbstractStepExecution_error_missingBuildData()));
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
    validateData();
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
