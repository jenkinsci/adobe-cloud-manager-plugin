package io.jenkins.plugins.adobe.cloudmanager.trigger;

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

import java.util.function.Predicate;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import io.jenkins.plugins.adobe.cloudmanager.util.DescriptorHelper;
import jenkins.model.ParameterizedJobMixIn;
import lombok.Getter;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineStartTrigger extends Trigger<Job<?, ?>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStartTrigger.class);

  @Getter
  private final String aioProject;
  @Getter
  private final String program;
  @Getter
  private final String pipeline;

  @DataBoundConstructor
  public PipelineStartTrigger(String aioProject, String program, String pipeline) throws AbortException {

    CloudManagerApi api = createApi(aioProject);
    this.aioProject = aioProject;
    this.program = getProgramId(api, program);
    this.pipeline = getPipelineId(api, this.program, pipeline);
  }

  /**
   * Predicate for stream processing.
   */
  public static Predicate<PipelineStartTrigger> interestedIn(PipelineStartEvent event) {
    return (trigger) ->
        StringUtils.equals(trigger.getAioProject(), event.getAioProject()) &&
        StringUtils.equals(trigger.getProgramId(), event.getProgramId()) &&
        StringUtils.equals(trigger.getPipelineId(), event.getPipelineId());
  }


  @Nonnull
  private static CloudManagerApi createApi(String aioProject) throws AbortException {
    return CloudManagerApiUtil.createApi().apply(aioProject).orElseThrow(() -> new AbortException(Messages.PipelineStartTrigger_error_missingAioProject(aioProject)));
  }

  @Nonnull
  private static String getProgramId(CloudManagerApi api, String program) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(program));
    } catch (NumberFormatException e) {
      LOGGER.debug(Messages.PipelineStartTrigger_debug_lookupProgramId(program));
      return CloudManagerApiUtil.getProgramId(api, program).orElseThrow(() -> new AbortException(Messages.PipelineStartTrigger_error_missingProgram(program)));
    }
  }

  @Nonnull
  private static String getPipelineId(CloudManagerApi api, String programId, String pipeline) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(pipeline));
    } catch (NumberFormatException e) {
      LOGGER.debug(Messages.PipelineStartTrigger_debug_lookupPipelineId(programId));
      return CloudManagerApiUtil.getPipelineId(api, programId, pipeline).orElseThrow(() -> new AbortException(Messages.PipelineStartTrigger_error_missingPipeline(pipeline)));
    }
  }

  public String getAioProject() {
    return aioProject;
  }

  public String getProgramId() {
    return program;
  }

  public String getPipelineId() {
    return pipeline;
  }

  public void onEvent(PipelineStartEvent event) {
    if (job == null) {
      return; // nothing to do, no job to start.
    }
    if (!(job instanceof ParameterizedJobMixIn.ParameterizedJob)) {
      LOGGER.warn(Messages.PipelineStartTrigger_warn_invalidJob(job.getName()));
      return; // Invalid job type.
    }

    LOGGER.debug(Messages.PipelineStartTrigger_debug_startJob(event.getAioEventId()));
    CauseAction ca = new CauseAction(new CMPipelineStartCause(event.getAioEventId()));
    CloudManagerBuildAction buildAction = new CloudManagerBuildAction(event.getAioProject(), new CloudManagerPipelineExecution(event.getProgramId(), event.getPipelineId(), event.getExecutionId()));
    ((ParameterizedJobMixIn.ParameterizedJob) job).scheduleBuild2(0, ca, buildAction);
  }

  @Extension
  @Symbol("acmPipelineStart")
  public static final class DescriptorImpl extends TriggerDescriptor {

    @Override
    public boolean isApplicable(Item item) {
      return item instanceof Job && item instanceof ParameterizedJobMixIn.ParameterizedJob;
    }

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

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Adobe Cloud Manager Pipeline Start";
    }
  }
}
