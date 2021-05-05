package io.jenkins.plugins.adobe.cloudmanager.project;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.DescriptorHelper;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudManagerProjectProperty extends JobProperty<Job<?, ?>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerProjectProperty.class);

  private String aioProject;
  private String program;
  private String pipeline;

  @DataBoundConstructor
  public CloudManagerProjectProperty(String aioProject, String program, String pipeline) {
    this.aioProject = aioProject;
    this.program = program;
    this.pipeline = pipeline;
  }

  @CheckForNull
  public String getAioProject() {
    return this.aioProject;
  }

  @CheckForNull
  public String getProgram() {
    return this.program;
  }

  @CheckForNull
  public String getPipeline() {
    return this.pipeline;
  }

  @Extension
  public static final class DescriptorImpl extends JobPropertyDescriptor {

    public static final String ADOBE_CLOUD_MANAGER_PROJECT_BLOCK_NAME = "acmProject";

    @Override
    public boolean isApplicable(Class<? extends Job> jobType) {
      return ParameterizedJobMixIn.ParameterizedJob.class.isAssignableFrom(jobType);
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.CloudManagerProjectProperty_DescriptorImpl_displayName();
    }

    @Override
    public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
      CloudManagerProjectProperty cmpp = req.bindJSON(CloudManagerProjectProperty.class, formData.getJSONObject(ADOBE_CLOUD_MANAGER_PROJECT_BLOCK_NAME));
      if (cmpp == null) {
        LOGGER.trace(Messages.CloudManagerProjectProperty_DescriptorImpl_trace_jsonBind());
        return null;
      }

      if (cmpp.getAioProject() == null) {
        return null;
      }
      AdobeIOProjectConfig cfg = AdobeIOConfig.projectConfigFor(cmpp.getAioProject());
      if (cfg == null) {
        LOGGER.trace(Messages.CloudManagerProjectProperty_DescriptorImpl_trace_missingAioProject());
        return null;
      }

      return cmpp;
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
  }
}
