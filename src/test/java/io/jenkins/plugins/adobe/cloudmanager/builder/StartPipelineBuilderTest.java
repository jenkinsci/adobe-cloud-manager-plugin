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

import java.util.ArrayList;
import java.util.List;

import hudson.model.Label;
import hudson.model.Result;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.Program;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.DescriptorHelperTest;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class StartPipelineBuilderTest {


  public static final String programId = "1101";
  public static final String pipelineId = "2202";

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Mocked
  private CloudManagerApi api;

  @Before
  public void before() {
    new MockUp<StartPipelineBuilder>() {
      @Mock
      public CloudManagerApi createApi() {
        return api;
      }

      @Mock
      public String getProgramId(CloudManagerApi api) {
        return programId;
      }
      @Mock
      public String getPipelineId(CloudManagerApi api, String programId) {
        return pipelineId;
      }
    };
  }

  @Test
  public void roundTrip() throws Exception {
    setupAdobeIOConfigs(rule.jenkins);
    AdobeIOProjectConfig aioConfig = AIO_PROJECT_CONFIGS.get(0);
    List<Program> programs = new ArrayList<>();
    programs.add(new DescriptorHelperTest.ProgramImpl("1", "Program"));

    List<Pipeline> pipelines = new ArrayList<>();
    pipelines.add(new DescriptorHelperTest.PipelineImpl("2", "Pipeline"));

    new Expectations(aioConfig) {{
      aioConfig.authenticate();
      result = Secret.fromString(ACCESS_TOKEN);
      api.listPrograms();
      result = programs;
      api.listPipelines(anyString);
      result = pipelines;
    }};
    StartPipelineBuilder builder = new StartPipelineBuilder();
    builder.setAioProject(AIO_PROJECT_NAME);
    builder.setProgram("1");
    builder.setPipeline("2");

    StartPipelineBuilder roundtrip = rule.configRoundtrip(builder);
    rule.assertEqualDataBoundBeans(builder, roundtrip);
  }

  @Test
  public void apiFails() throws Exception {

    new Expectations() {{
      api.startExecution(programId, pipelineId);
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.PIPELINE_START, "Error");
    }};

    rule.createOnlineSlave(Label.get("runner"));
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "    acmStartPipeline(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + programId + "', pipeline: '" + pipelineId + "')\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
    assertTrue(run.getLog().contains("An API exception occurred"));
  }


  @Test
  public void duplicateSteps(@Mocked PipelineExecution execution) throws Exception {

    final String executionId = "3303";

    new Expectations() {{
      api.startExecution(programId, pipelineId);
      result = execution;
      execution.getProgramId();
      result = programId;
      execution.getPipelineId();
      result = pipelineId;
      execution.getId();
      result = executionId;
    }};

    rule.createOnlineSlave(Label.get("runner"));
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "    acmStartPipeline(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + programId + "', pipeline: '" + pipelineId + "')\n" +
            "    acmStartPipeline(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + programId + "', pipeline: '" + pipelineId + "')\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
    assertTrue(run.getLog().contains(Messages.StartPipelineBuilder_error_duplicateBuild()));
  }

  @Test
  public void success(@Mocked PipelineExecution execution) throws Exception {

    final String executionId = "3303";

    new Expectations() {{
      api.startExecution(programId, pipelineId);
      result = execution;
      execution.getProgramId();
      result = programId;
      execution.getPipelineId();
      result = pipelineId;
      execution.getId();
      result = executionId;
    }};

    rule.createOnlineSlave(Label.get("runner"));
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "    acmStartPipeline(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + programId + "', pipeline: '" + pipelineId + "')\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    assertTrue(run.getLog().contains(Messages.StartPipelineBuilder_started(executionId, pipelineId)));
    CloudManagerBuildAction action = run.getAction(CloudManagerBuildAction.class);
    assertNotNull(action);
    assertEquals(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution(programId, pipelineId, executionId)), action);
  }
}
