package io.jenkins.plugins.adobe.cloudmanager.util;

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

import java.util.Collections;

import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.Program;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.test.TestHelper;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class CloudManagerApiUtilTest {

  private final String aioProject = AIO_PROJECT_NAME;
  @Mocked
  private AdobeIOConfig aioConfig;
  @Mocked
  private AdobeIOProjectConfig adobeIOProjectConfig;

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private Program program;

  @Mocked
  private Pipeline pipeline;

  @Test
  public void createApiMissingAioProject() {
    new Expectations() {{
      AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME);
      result = null;
    }};
    assertFalse(CloudManagerApiUtil.createApi().apply(aioProject).isPresent());
  }

  @Test
  public void createApiUnableToAuthenticate() {
    new Expectations() {{
      AdobeIOConfig.projectConfigFor(TestHelper.AIO_PROJECT_NAME);
      result = adobeIOProjectConfig;
      adobeIOProjectConfig.authenticate();
      result = null;
    }};
    assertFalse(CloudManagerApiUtil.createApi().apply(aioProject).isPresent());
  }

  @Test
  public void createApiSuccess() throws Exception {
    new Expectations() {{
      AdobeIOConfig.projectConfigFor(TestHelper.AIO_PROJECT_NAME);
      result = adobeIOProjectConfig;
      adobeIOProjectConfig.authenticate();
      result = Secret.fromString(ACCESS_TOKEN);
    }};

    assertTrue(CloudManagerApiUtil.createApi().apply(aioProject).isPresent());
  }

  @Test
  public void programIdNameNotFound() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = Collections.emptyList();
    }};
    assertFalse(CloudManagerApiUtil.getProgramId(api, "Not Found").isPresent());
  }

  @Test
  public void programIdApiError() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "1");
    }};
    assertFalse(CloudManagerApiUtil.getProgramId(api, "Api Exception").isPresent());
  }

  @Test
  public void programIdNameFound() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = Collections.singletonList(program);
      program.getName();
      result = "Found";
      program.getId();
      result = "1";
    }};
    assertEquals("1", CloudManagerApiUtil.getProgramId(api, "Found").get());
  }

  @Test
  public void pipelineIdNameNotFound() throws Exception {
    new Expectations() {{
      api.listPipelines("1", withInstanceOf(Pipeline.NamePredicate.class));
      result = Collections.emptyList();
    }};
    assertFalse(CloudManagerApiUtil.getPipelineId(api, "1", "Not Found").isPresent());
  }

  @Test
  public void pipelineIdApiError() throws Exception {
    new Expectations() {{
      api.listPipelines("1", withInstanceOf(Pipeline.NamePredicate.class));
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "1");
    }};
    assertFalse(CloudManagerApiUtil.getPipelineId(api, "1", "Api Exception").isPresent());
  }

  @Test
  public void pipelineIdNameFound() throws Exception {
    new Expectations() {{
      api.listPipelines("1", withInstanceOf(Pipeline.NamePredicate.class));
      result = Collections.singletonList(pipeline);
      pipeline.getId();
      result = "2";
    }};
    assertEquals("2", CloudManagerApiUtil.getPipelineId(api, "1", "Found").get());
  }
}
