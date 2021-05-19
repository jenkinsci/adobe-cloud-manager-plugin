package io.jenkins.plugins.adobe.cloudmanager.config;

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
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

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

  // XML Config file name for storage.
  public static final String CLOUD_MANAGER_CONFIGURATION_ID = "adobe-cloud-manager-plugin-config";

  private static final Logger LOGGER = LoggerFactory.getLogger(AdobeIOConfig.class);

  // Protect against no configurations done, and NPE.
  private static final AdobeIOConfig EMPTY_CONFIG = new AdobeIOConfig(Collections.emptyList());

  private List<AdobeIOProjectConfig> projectConfigs = new ArrayList<>();

  // Webhook is disabled by default - make a conscious decision to enable it.
  private boolean webhookEnabled = false;

  public AdobeIOConfig() {
    getConfigFile().getXStream().alias("adobe-io-project-config", AdobeIOProjectConfig.class);
    load();
  }

  public AdobeIOConfig(@Nonnull List<AdobeIOProjectConfig> projectConfigs) {
    this.projectConfigs = projectConfigs;
  }

  /**
   * Helper for looking up this configuration, protecting against null.
   */
  @Nonnull
  public static AdobeIOConfig configuration() {
    AdobeIOConfig config = AdobeIOConfig.all().get(AdobeIOConfig.class);
    if (config == null) {
      config = EMPTY_CONFIG;
    }
    return config;
  }

  /**
   * Helper to find an AIO Project config based on a name.
   * <p>
   *   Name must match exactly.
   * </p>
   * <p>
   *   Useful for checking after restarts or for long running builds.
   * </p>
   *
   * @param name the name of the AIO Project Config
   * @return config or null
   */
  @CheckForNull
  public static AdobeIOProjectConfig projectConfigFor(@Nonnull String name) {
    return AdobeIOConfig.configuration().getProjectConfigs()
        .stream()
        .filter(c -> StringUtils.equals(name, c.getName()))
        .findFirst().orElse(null);
  }

  @Nonnull
  public List<AdobeIOProjectConfig> getProjectConfigs() {
    return Collections.unmodifiableList(projectConfigs);
  }

  @DataBoundSetter
  public void setProjectConfigs(@Nonnull List<AdobeIOProjectConfig> projectConfigs) {
    this.projectConfigs = projectConfigs;
  }

  public boolean isWebhookEnabled() {
    return webhookEnabled;
  }

  @DataBoundSetter
  public void setWebhookEnabled(boolean webhookEnabled) {
    this.webhookEnabled = webhookEnabled;
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
}
