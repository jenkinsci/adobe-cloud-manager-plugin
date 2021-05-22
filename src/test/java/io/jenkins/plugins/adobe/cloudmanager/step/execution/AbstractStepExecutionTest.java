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

import hudson.AbortException;
import hudson.model.Run;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AbstractStepExecutionTest {

  private static final String found = "Found Config";

  @Tested
  private AbstractStepExecution tested;

  @Injectable
  private StepContext context;

  @Mocked
  private Run<?, ?> run;

  @Mocked
  private CloudManagerBuildAction data;

  @Mocked
  private CloudManagerPipelineExecution cmExecution;

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private AdobeIOProjectConfig projectConfig;

  @Before
  public void before() throws Exception {
    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return found.equals(name) ? projectConfig : null;
      }
    };
    new Expectations() {{
      context.get(Run.class);
      result = run;
      minTimes = 0;
      run.getAction(CloudManagerBuildAction.class);
      result = data;
      minTimes = 0;
    }};
  }

  @Test
  public void validateMissingAIOProject() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = "Missing";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingCMExecution() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getCmExecution();
      result = null;
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateSuccess() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getCmExecution();
      result = cmExecution;
    }};
    tested.validateData();
  }

  @Test
  public void projectMissing() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = "Missing";
    }};
    assertThrows(AbortException.class, () -> tested.getAioProject());
  }

  @Test
  public void projectFound() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
    }};
    assertNotNull(tested.getAioProject());
  }
}
