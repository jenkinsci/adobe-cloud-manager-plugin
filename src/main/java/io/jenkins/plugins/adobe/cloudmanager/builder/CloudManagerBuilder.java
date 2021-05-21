package io.jenkins.plugins.adobe.cloudmanager.builder;

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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import io.jenkins.plugins.adobe.cloudmanager.util.DescriptorHelper;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper builder for steps which interact with Cloud Manager.
 * <p>
 * Any {@link org.jenkinsci.plugins.workflow.steps.Step} which interact with Cloud Manager and can be expressed as a {@link Builder} should extend this class.
 * </p>
 */
public abstract class CloudManagerBuilder extends Builder implements SimpleBuildStep {
  protected static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerBuilder.class);

  protected String aioProject;
  protected String program;
  protected String pipeline;

  protected CloudManagerBuilder() {
  }

  @CheckForNull
  public String getAioProject() {
    return aioProject;
  }

  @DataBoundSetter
  public void setAioProject(String aioProject) {
    this.aioProject = aioProject;
  }

  @CheckForNull
  public String getProgram() {
    return program;
  }

  @DataBoundSetter
  public void setProgram(String program) {
    this.program = program;
  }

  @CheckForNull
  public String getPipeline() {
    return pipeline;
  }

  @DataBoundSetter
  public void setPipeline(String pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Create a {@link CloudManagerApi} from this Builder's configured Adobe IO project.
   */
  @Nonnull
  public CloudManagerApi createApi() throws AbortException {
    return CloudManagerApiUtil.createApi().apply(aioProject).orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingAioProject(aioProject)));
  }

  /**
   * Get the Program Id for this Builder's configured Program. Program can be specified as Id or Name.
   */
  @Nonnull
  public String getProgramId(CloudManagerApi api) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(program));
    } catch (NumberFormatException e) {
      LOGGER.debug(Messages.CloudManagerBuilder_debug_lookupProgramId(program));
      return CloudManagerApiUtil.getProgramId(api, program).orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingProgram(program)));
    }
  }

  /**
   * Get the Pipeline ID for this builder's configured Pipeline. Pipelines can be specified as an Id or Name.
   */
  @Nonnull
  public String getPipelineId(CloudManagerApi api, String programId) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(pipeline));
    } catch (NumberFormatException e) {
      LOGGER.debug(Messages.CloudManagerBuilder_debug_lookupPipelineId(program));
      return CloudManagerApiUtil.getPipelineId(api, programId, pipeline).orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingPipeline(pipeline)));
    }
  }

  /**
   * Helper descriptor for concrete classes of Cloud Manager Builders.
   */
  public static class CloudManagerBuilderDescriptor extends BuildStepDescriptor<Builder> {

    /**
     * Lists the Adobe IO Projects available.
     */
    public ListBoxModel doFillAioProjectItems() {
      return DescriptorHelper.fillAioProjectItems();
    }

    /**
     * List the Programs available based on the selected Adobe IO Project.
     */
    public ListBoxModel doFillProgramItems(@QueryParameter String aioProject) {
      return DescriptorHelper.fillProgramItems(aioProject);
    }

    /**
     * List the Pipelines associated with the selected Program.
     */
    public ListBoxModel doFillPipelineItems(@QueryParameter String aioProject, @QueryParameter String program) {
      return DescriptorHelper.fillPipelineItems(aioProject, program);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }
  }
}
