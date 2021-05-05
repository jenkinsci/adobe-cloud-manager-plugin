package io.jenkins.plugin.adobe.cloudmanager.builder;

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
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import hudson.AbortException;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineUpdate;
import io.adobe.cloudmanager.Program;
import io.adobe.cloudmanager.Variable;
import io.jenkins.plugin.adobe.cloudmanager.test.TestHelper;
import io.jenkins.plugin.adobe.cloudmanager.util.DescriptorHelperTest;
import io.jenkins.plugins.adobe.cloudmanager.builder.CloudManagerBuilder;
import io.jenkins.plugins.adobe.cloudmanager.builder.Messages;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.DescriptorHelper;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.BeforeClass;
import org.junit.Test;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class CloudManagerBuilderTest {

  @Injectable
  private final String aioProject = AIO_PROJECT_NAME;

  @Injectable
  private final String program = "Program Name";
  private static final String PROGRAM_ID = "2";

  @Injectable
  private final String pipeline = "Pipeline Name";
  private static final String PIPELINE_ID = "4";

  @Tested
  private CloudManagerBuilder builder;

  @Mocked
  private AdobeIOConfig aioConfig;

  @Mocked
  private AdobeIOProjectConfig adobeIOProjectConfig;

  @Mocked
  private CloudManagerApi api;

  private static final List<Program> programs = new ArrayList<>();;
  private static final List<Pipeline> pipelines = new ArrayList<>();;

  @BeforeClass
  public static void beforeClass() {
    programs.add(new DescriptorHelperTest.ProgramImpl("1", "Another Program"));
    programs.add(new DescriptorHelperTest.ProgramImpl(PROGRAM_ID, "Program Name"));
    pipelines.add(new DescriptorHelperTest.PipelineImpl(PIPELINE_ID, "Pipeline Name"));
  }

  @Test
  public void missingAioProject() {
    new Expectations() {{
      AdobeIOConfig.projectConfigFor(TestHelper.AIO_PROJECT_NAME);
      result = null;
    }};

    AbortException exception = assertThrows(AbortException.class, () -> builder.createApi());
    assertEquals(Messages.CloudManagerBuilder_error_missingAioProject(AIO_PROJECT_NAME), exception.getLocalizedMessage());
  }

  @Test
  public void unableToAuthenticate() {
    new Expectations() {{
      AdobeIOConfig.projectConfigFor(TestHelper.AIO_PROJECT_NAME);
      result = adobeIOProjectConfig;
      adobeIOProjectConfig.authenticate();
      result = null;
    }};

    AbortException exception = assertThrows(AbortException.class, () -> builder.createApi());
    assertEquals(Messages.CloudManagerBuilder_error_authenticate(Messages.CloudManagerBuilder_error_checkLogs()), exception.getLocalizedMessage());
  }

  @Test
  public void createApi() throws Exception {
    new Expectations() {{
      AdobeIOConfig.projectConfigFor(TestHelper.AIO_PROJECT_NAME);
      result = adobeIOProjectConfig;
      adobeIOProjectConfig.authenticate();
      result = Secret.fromString(ACCESS_TOKEN);
    }};

    assertNotNull(builder.createApi());
  }

  @Test
  public void missingProgram() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = Collections.emptyList();
    }};

    AbortException exception = assertThrows(AbortException.class, () -> builder.getProgramId(api));
    assertEquals(Messages.CloudManagerBuilder_error_missingProgram(program), exception.getLocalizedMessage());
  }

  @Test
  public void programApiError() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, program);
    }};
    AbortException exception = assertThrows(AbortException.class, () -> builder.getProgramId(api));
    assertTrue(StringUtils.contains(exception.getLocalizedMessage(), "An API exception occurred"));
  }

  @Test
  public void getProgramIdFromName() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = programs;
    }};
    assertEquals(PROGRAM_ID, builder.getProgramId(api));
  }

  @Test
  public void getProgramIdFromId(@Tested CloudManagerBuilder localBuilder) throws Exception {
    localBuilder.setProgram(PROGRAM_ID);
    assertEquals(PROGRAM_ID, localBuilder.getProgramId(api));
  }


  @Test
  public void missingPipeline() throws Exception {
    new Expectations() {{
      api.listPipelines(PROGRAM_ID, withNotNull());
      result = Collections.emptyList();
    }};
    AbortException exception = assertThrows(AbortException.class, () -> builder.getPipelineId(api, PROGRAM_ID));
    assertEquals(Messages.CloudManagerBuilder_error_missingPipeline(pipeline), exception.getLocalizedMessage());
  }

  @Test
  public void pipelineApiError() throws Exception {
    new Expectations() {{
      api.listPipelines(PROGRAM_ID, withNotNull());
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PIPELINE, pipeline, PROGRAM_ID);
    }};

    AbortException exception = assertThrows(AbortException.class, () -> builder.getPipelineId(api, PROGRAM_ID));
    assertTrue(StringUtils.contains(exception.getLocalizedMessage(), "An API exception occurred"));
  }

  @Test
  public void getPipelineIdFromName() throws Exception {
    new Expectations() {{
      api.listPipelines(PROGRAM_ID, withNotNull());
      result = pipelines;
    }};

    assertEquals(PIPELINE_ID, builder.getPipelineId(api, PROGRAM_ID));
  }

  @Test
  public void getPipelineIdFromId(@Tested CloudManagerBuilder localBuilder) throws Exception {
    localBuilder.setPipeline(PIPELINE_ID);
    assertEquals(PIPELINE_ID, localBuilder.getPipelineId(api, PROGRAM_ID));
  }

}
