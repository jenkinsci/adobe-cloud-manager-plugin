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

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nonnull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartPipelineBuilder extends CloudManagerBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartPipelineBuilder.class);

  @DataBoundConstructor
  public StartPipelineBuilder() {
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull EnvVars env, @Nonnull TaskListener listener) throws InterruptedException, IOException {
    CloudManagerApi api = createApi();
    String programId = getProgramId(api);
    String pipelineId = getPipelineId(api, programId);

    if (run.getAction(CloudManagerBuildAction.class) != null) {
      throw new AbortException(Messages.StartPipelineBuilder_error_duplicateBuild());
    }
    try {
      PrintStream log = listener.getLogger();
      PipelineExecution execution = api.startExecution(programId, pipelineId);
      CloudManagerBuildAction data = new CloudManagerBuildAction(getAioProject(), execution.getProgramId(), execution.getPipelineId(), execution.getId());
      run.addAction(data);
      log.println(Messages.StartPipelineBuilder_started(execution.getId(), pipeline));
    } catch (CloudManagerApiException e) {
      throw new AbortException(Messages.CloudManagerBuilder_error_CloudManagerApiException(e.getLocalizedMessage()));
    }
  }

  @Override
  @NonNull
  public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
    return Collections.singletonList(new CloudManagerBuildAction());
  }

  @Override
  public boolean requiresWorkspace() {
    return false;
  }

  @Symbol("acmStartPipeline")
  @Extension
  public static class DescriptorImpl extends CloudManagerBuilderDescriptor {

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.StartPipelineBuilder_displayName();
    }
  }
}
