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

import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.DescriptorHelper;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CloudManagerBuilder extends Builder implements SimpleBuildStep {
  protected static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerBuilder.class);

  protected String aioProject;
  protected String program;
  protected String pipeline;

  protected CloudManagerBuilder() {

  }

  public String getAioProject() {
    return aioProject;
  }

  @DataBoundSetter
  public void setAioProject(String aioProject) {
    this.aioProject = aioProject;
  }

  public String getProgram() {
    return program;
  }

  @DataBoundSetter
  public void setProgram(String program) {
    this.program = program;
  }

  public String getPipeline() {
    return pipeline;
  }

  @DataBoundSetter
  public void setPipeline(String pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Authenticate using this Builder's configured Adobe IO Project, and return an access token
   *
   * @param config the configuration to use for authentication
   * @return access token
   * @throws AbortException when any error occur during authentication
   */
  @Nonnull
  private Secret getAccessToken(@Nonnull AdobeIOProjectConfig config) throws AbortException {
    Secret token = config.authenticate();
    if (token == null) {
      throw new AbortException(Messages.CloudManagerBuilder_error_authenticate(
          Messages.CloudManagerBuilder_error_checkLogs()
      ));
    }
    return token;
  }

  /**
   * Create a {@link CloudManagerApi} from this Builder's configured Adobe IO project.
   *
   * @return a CloudManagerApi
   * @throws AbortException when any error occurs during API construction
   */
  @Nonnull
  public CloudManagerApi createApi() throws AbortException {
    AdobeIOProjectConfig config = AdobeIOConfig.projectConfigFor(aioProject);
    if (config == null) {
      throw new AbortException(Messages.CloudManagerBuilder_error_missingAioProject(aioProject));
    }
    return CloudManagerApi.create(config.getImsOrganizationId(), config.getClientId(), getAccessToken(config).getPlainText());
  }

  /**
   * Get the Program Id for this Builder's configured Program. Program can be specified as id or Name.
   *
   * @param api configured API for looking up the ProgramId.
   * @return the program id (as a String)
   * @throws AbortException when any error occurs finding the id
   */
  public String getProgramId(CloudManagerApi api) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(program));
    } catch (NumberFormatException e) {
      try {
        return api.listPrograms()
            .stream()
            .filter(p -> program.equals(p.getName()))
            .findFirst()
            .orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingProgram(program)))
            .getId();
      } catch (CloudManagerApiException ex) {
        throw new AbortException(Messages.CloudManagerBuilder_error_CloudManagerApiException(ex.getLocalizedMessage()));
      }
    }
  }

  /**
   * Get the Pipeline ID for this builder's configured Pipeline. Pipelines can be specified as an Id or Name.
   *
   * @param api configured API for looking up pipeline id
   * @param programId the program id context. Must be the <i>id</i>
   * @return the pipeline id (as a String)
   * @throws AbortException when any error occurs finding the id
   */
  public String getPipelineId(CloudManagerApi api, String programId) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(pipeline));
    } catch (NumberFormatException e) {
      try {
      return api.listPipelines(programId, new Pipeline.NamePredicate(pipeline))
          .stream()
          .findFirst()
          .orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingPipeline(pipeline)))
          .getId();
      } catch (CloudManagerApiException ex) {
        throw new AbortException(Messages.CloudManagerBuilder_error_CloudManagerApiException(ex.getLocalizedMessage()));
      }
    }
  }

  public static class CloudManagerBuilderDescriptor extends BuildStepDescriptor<Builder> {

    protected static CloudManagerApi createApi(String aioProject) {
      AdobeIOProjectConfig cfg = AdobeIOConfig.projectConfigFor(aioProject);

      if (cfg == null) {
        return null;
      }
      Secret token = cfg.authenticate();
      if (token == null) {
        return null;
      }
      return CloudManagerApi.create(cfg.getImsOrganizationId(), cfg.getClientId(), token.getPlainText());
    }

    public ListBoxModel doFillAioProjectItems() {
      return DescriptorHelper.fillAioProjectItems();
    }

    public ListBoxModel doFillProgramItems(@QueryParameter String aioProject) {
      return DescriptorHelper.fillProgramItems(aioProject);
    }

    public ListBoxModel doFillPipelineItems(@QueryParameter String aioProject, @QueryParameter String program) {
      return DescriptorHelper.fillPipelineItems(aioProject, program);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }
  }
}
