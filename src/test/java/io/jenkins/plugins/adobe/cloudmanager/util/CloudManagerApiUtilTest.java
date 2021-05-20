package io.jenkins.plugins.adobe.cloudmanager.util;

import hudson.util.Secret;
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
}
