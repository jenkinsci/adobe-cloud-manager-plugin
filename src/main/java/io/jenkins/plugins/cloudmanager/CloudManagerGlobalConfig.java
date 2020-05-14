package io.jenkins.plugins.cloudmanager;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.swagger.client.StringUtil;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/** Example of Jenkins global configuration. */
@Extension
public class CloudManagerGlobalConfig extends GlobalConfiguration implements AdobeioConfig {

  private String apiKey, organizationID, technicalAccountId;
  private Secret clientSecret, privateKey;

  // not tied to a field, contains the runtime secret
  private String accessToken;

  /** @return the singleton instance */
  public static CloudManagerGlobalConfig get() {
    return GlobalConfiguration.all().get(CloudManagerGlobalConfig.class);
  }

  public CloudManagerGlobalConfig() {
    // When Jenkins is restarted, load any saved configuration from disk.
    load();
  }

  public String refreshAccessToken() {
    this.accessToken = null;
    this.accessToken = getFreshAccessToken(this);
    return this.accessToken;
  }

  private static String getFreshAccessToken(AdobeioConfig config) {
    return CloudManagerAuthUtil.getAccessToken(config);
  }
  // GETTERS / SETTERS
  public String getAccessToken() {
    if (StringUtils.isBlank(accessToken)) {
      accessToken = refreshAccessToken();
    }
    return accessToken;
  }

  public String getApiKey() {
    return apiKey;
  }

  @DataBoundSetter
  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
    save();
  }

  public String getOrganizationID() {
    return organizationID;
  }

  @DataBoundSetter
  public void setOrganizationID(String organizationID) {
    this.organizationID = organizationID;
    save();
  }

  public String getTechnicalAccountId() {
    return technicalAccountId;
  }

  @DataBoundSetter
  public void setTechnicalAccountId(String technicalAccountId) {
    this.technicalAccountId = technicalAccountId;
    save();
  }

  public Secret getClientSecret() {
    return clientSecret;
  }

  @DataBoundSetter
  public void setClientSecret(Secret clientSecret) {
    this.clientSecret = clientSecret;
    save();
  }

  public Secret getPrivateKey() {
    return privateKey;
  }

  @DataBoundSetter
  public void setPrivateKey(Secret privateKey) {
    this.privateKey = privateKey;
    save();
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @POST
  public FormValidation doTestAdobeioConnection(
      @QueryParameter("apiKey") final String apiKey,
      @QueryParameter("organizationID") final String organizationID,
      @QueryParameter("technicalAccountId") final String technicalAccountId,
      @QueryParameter("clientSecret") final Secret clientSecret,
      @QueryParameter("privateKey") final Secret privateKey) {

    // Jenkins.get().checkPermission(Jenkins.ADMINISTER);

    // test that we can successfully get an access token.
    AdobeioConfig config =
        new AdobeioConfigImpl(apiKey, organizationID, technicalAccountId, clientSecret, privateKey, null);
    String token = getFreshAccessToken(config);
    if (StringUtils.isNoneBlank(token)) {
      return FormValidation.okWithMarkup("Success! Save this configuration page.");
    } else {
      return FormValidation.errorWithMarkup(
          "Failed. But not sure where exactly. "
              + "A stack trace should have appeared instead of this message.");
    }
  }
}
