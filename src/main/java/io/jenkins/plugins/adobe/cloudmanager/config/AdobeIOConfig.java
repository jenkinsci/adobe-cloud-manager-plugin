package io.jenkins.plugins.adobe.cloudmanager.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global Configuration panel for Adobe IO projects.
 */
@Extension
public class AdobeIOConfig extends GlobalConfiguration {
  private static final Logger LOGGER = LoggerFactory.getLogger(AdobeIOConfig.class);
  public static final String CLOUD_MANAGER_CONFIGURATION_ID = "adobe-cloud-manager-plugin-config";

  private List<AdobeIOProjectConfig> projectConfigs = new ArrayList<>();

  public AdobeIOConfig() {
    getConfigFile().getXStream().alias("adobe-io-project-config", AdobeIOProjectConfig.class);
    load();
  }

  public AdobeIOConfig(@Nonnull List<AdobeIOProjectConfig> projectConfigs) {
    this.projectConfigs = projectConfigs;
  }

  /**
   * To avoid long class name as id in xml tag name and config file
   */
  @Override
  public String getId() {
    return CLOUD_MANAGER_CONFIGURATION_ID;
  }

  @Override
  public String getDisplayName() {
    return "Adobe IO";
  }

  @Override
  public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
    projectConfigs = new ArrayList<>(); // Form binding does not save empty lists properly.
    super.configure(req, json);
    save();
    return true;
  }

  @DataBoundSetter
  public void setProjectConfigs(@Nonnull List<AdobeIOProjectConfig> projectConfigs) {
    this.projectConfigs = projectConfigs;
  }

  @Nonnull
  public List<AdobeIOProjectConfig> getProjectConfigs() {
    return Collections.unmodifiableList(projectConfigs);
  }
}
