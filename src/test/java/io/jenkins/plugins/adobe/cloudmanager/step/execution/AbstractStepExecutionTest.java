package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import hudson.AbortException;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerBuildData;
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

  @Injectable
  private CloudManagerBuildData data;

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private AdobeIOProjectConfig projectConfig;

  @Before
  public void before() {
    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return found.equals(name) ? projectConfig : null;
      }
    };
  }

  @Test
  public void validateMissingAIOProject() {
    new Expectations() {{
      data.getAioProjectName();
      result = "Missing";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingProgramId() {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingPipelineId() {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getProgramId();
      result = "Program Id";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingExecutionId() {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getProgramId();
      result = "Program Id";
      data.getPipelineId();
      result = "Pipeline Id";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateSuccess() throws AbortException {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getProgramId();
      result = "Program Id";
      data.getPipelineId();
      result = "Pipeline Id";
      data.getExecutionId();
      result = "Execution Id";
    }};
    tested.validateData();
  }

  @Test
  public void projectMissing() {
    new Expectations() {{
      data.getAioProjectName();
      result = "Missing";
    }};
    assertThrows(AbortException.class, () -> tested.getAioProject());
  }

  @Test
  public void projectFound() throws AbortException{
    new Expectations() {{
      data.getAioProjectName();
      result = found;
    }};
    assertNotNull(tested.getAioProject());
  }

  @Test
  public void accessTokenFails() {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      projectConfig.authenticate();
      result = null;
    }};
    assertThrows(AbortException.class, () -> tested.getAccessToken());
  }

  @Test
  public void accessTokenSuccess() throws AbortException {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      projectConfig.authenticate();
      result = Secret.fromString("Test");
    }};
    assertNotNull(tested.getAccessToken());
  }
}
