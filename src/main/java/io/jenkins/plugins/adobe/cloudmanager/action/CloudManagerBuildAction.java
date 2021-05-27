package io.jenkins.plugins.adobe.cloudmanager.action;

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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.math.NumberUtils;

import hudson.model.Job;
import hudson.model.Run;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.ExportedBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.PipelineExecutionStepState.Status.*;

/**
 * Cloud Manager build data used for taking actions.
 */
@Value
@ExportedBean(defaultVisibility = 1500)
public class CloudManagerBuildAction implements PersistentAction, Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerBuildAction.class);
  private static final long serialVersionUID = 1L;
  private static final String SONAR_LOG = "sonarLogFile";
  private static final String STEP_PARAM = "step";

  String aioProjectName;
  CloudManagerPipelineExecution cmExecution;

  @EqualsAndHashCode.Exclude
  List<PipelineStep> steps = new CopyOnWriteArrayList<>();

  @Override
  public String getDisplayName() {
    return "Adobe Cloud Manager Build";
  }

  @Override
  public String getIconFileName() {
    return jenkins.model.Jenkins.RESOURCE_PATH + "/plugin/adobe-cloud-manager/icons/Adobe_Experience_Cloud_logo_48px.png";
  }

  @Override
  public String getUrlName() {
    return String.format("adobe-cloud-manager-p%s-p%s-e%s", cmExecution.getProgramId(), cmExecution.getPipelineId(), cmExecution.getExecutionId());
  }

  @CheckForNull
  public Run<?, ?> getOwningRun() {
    StaplerRequest req = Stapler.getCurrentRequest();
    if (req == null) {
      return null;
    }
    return req.findAncestorObject(Run.class);
  }

  public List<PipelineStep> getSteps() {
    return new ArrayList<>(this.steps);
  }

  public PipelineStep getStep(@Nonnull String id) {
    int i = NumberUtils.toInt(id, -1);
    if (i >= 0 && i < getSteps().size()) {
      return getSteps().get(i);
    } else {
      return null;
    }
  }

  public void addStep(@Nonnull PipelineStep step) {
    steps.add(step);
  }

  private boolean canDownload() {
    return getOwningRun().getParent().hasPermission(Job.READ);
  }

  public HttpResponse doGetLog() {

    final PipelineStep step = getPipelineStep();
    if (step == null) {
      return HttpResponses.redirectTo("../..");
    }
    if (step.getAction() == StepAction.codeQuality) {
      return getLogRedirect(step, SONAR_LOG);
    } else {
      return getLogRedirect(step, null);
    }
  }

  public HttpResponse doGetQualityData() {
    final PipelineStep step = getPipelineStep();
    if (step == null) {
      return HttpResponses.redirectTo("../..");
    }
    return getLogRedirect(step, null);
  }

  @CheckForNull
  private PipelineStep getPipelineStep() {
    int stepId = NumberUtils.toInt(Stapler.getCurrentRequest().getParameter(STEP_PARAM), -1);
    if (stepId < 0 || stepId > (getSteps().size() - 1)) {
      LOGGER.warn(Messages.CloudManagerBuildAction_warn_unknownStep());
      return null;
    }
    if (!canDownload()) {
      return null;
    }
    final PipelineStep step = getSteps().get(stepId);
    if (!step.isHasLogs()) {
      LOGGER.warn(Messages.CloudManagerBuildAction_warn_unknownStep());
      return null;
    }
    return step;
  }

  @Nonnull
  private HttpResponse getLogRedirect(PipelineStep step, String fileName) {
    Optional<CloudManagerApi> api = CloudManagerApiUtil.createApi().apply(getAioProjectName());
    if (api.isPresent()) {
      try {
        final CloudManagerPipelineExecution cmExecution = getCmExecution();
        String url = api.get().getExecutionStepLogDownloadUrl(cmExecution.getProgramId(), cmExecution.getPipelineId(), cmExecution.getExecutionId(), step.getAction().name(), fileName);
        return HttpResponses.redirectTo(url);
      } catch (CloudManagerApiException e) {
        return HttpResponses.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Messages.CloudManagerBuildAction_error_downloadLogs(e.getLocalizedMessage()));
      }
    } else {
      return HttpResponses.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Messages.CloudManagerBuildAction_error_downloadLogs_creatApi());
    }
  }

  /**
   * Represents a step which a Cloud Manager Pipeline execution reached.
   * <p>
   * Used in {@link CloudManagerBuildAction} for tracking state and linking logs.
   */
  @Data
  public static class PipelineStep implements Serializable {

    private static final long serialVersionUID = 1L;

    private final StepAction action;
    private final PipelineExecutionStepState.Status status;
    private final boolean hasLogs;

    public String getStatus() {
      return Messages.CloudManagerBuildAction_PipelineStep_status(action, status);
    }

    public String getLogName() {
      CloudManagerBuildAction buildData = Stapler.getCurrentRequest().findAncestorObject(CloudManagerBuildAction.class);
      String format = "%s-%s";
      if (buildData == null) {
        format = "%s";
      }
      return String.format(format, buildData.getUrlName(), action);
    }

    public boolean isHasQualityData() {
      return action == StepAction.codeQuality && (status == FINISHED || status == WAITING || status == ERROR);
    }
  }
}
