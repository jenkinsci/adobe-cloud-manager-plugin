package io.jenkins.plugin.adobe.cloudmanager.config;

import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdobeIOProjectConfigTest {

  @Test
  public void shouldMatchDisplayName() {
    AdobeIOProjectConfig cfg = new AdobeIOProjectConfig();
    cfg.setName("Test Name");
    cfg.setImsOrganizationId("IMS Org Id");
    assertEquals("Test Name (IMS Org Id)", cfg.getDisplayName());
  }
}
