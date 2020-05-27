package io.jenkins.plugins.cloudmanager;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/** Example of Jenkins global configuration. */
@Extension
public class CloudManagerGlobalConfig extends GlobalConfiguration implements AdobeioConfig {

  private String organizationID, technicalAccountId;
  private Secret clientSecret, privateKey, apiKey;

  // not tied to a field, contains the runtime secret
  private transient String accessToken;

  public CloudManagerGlobalConfig() {
    // When Jenkins is restarted, load any saved configuration from disk.
    load();
  }

  /** @return the singleton instance */
  public static CloudManagerGlobalConfig get() {
    return GlobalConfiguration.all().get(CloudManagerGlobalConfig.class);
  }

  private static String getFreshAccessToken(AdobeioConfig config) throws AdobeIOException {
    return CloudManagerAuthUtil.getAccessToken(config);
  }

  public String refreshAccessToken() throws AdobeIOException {
    this.accessToken = null;
    this.accessToken = getFreshAccessToken(this);
    return this.accessToken;
  }

  // GETTERS / SETTERS
  public String getAccessToken() throws AdobeIOException {
    if (StringUtils.isBlank(accessToken)) {
      accessToken = refreshAccessToken();
    }
    return accessToken;
  }

  public Secret getApiKey() {
    return apiKey;
  }

  @DataBoundSetter
  public void setApiKey(Secret apiKey) {
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
      @QueryParameter("apiKey") final Secret apiKey,
      @QueryParameter("organizationID") final String organizationID,
      @QueryParameter("technicalAccountId") final String technicalAccountId,
      @QueryParameter("clientSecret") final Secret clientSecret,
      @QueryParameter("privateKey") final Secret privateKey) {

    Jenkins.get().checkPermission(Jenkins.ADMINISTER);

    // test that we can successfully get an access token.
    AdobeioConfig config =
        new AdobeioConfigImpl(
            apiKey, organizationID, technicalAccountId, clientSecret, privateKey, null);
    try {
      String token = getFreshAccessToken(config);
      if (StringUtils.isNoneBlank(token)) {
        return FormValidation.okWithMarkup("Success! Save this configuration page.");
      } else {
        return FormValidation.errorWithMarkup(
            "Got a blank access token for some reason, check jenkins logs.");
      }
    } catch (AdobeIOException e) {
      return FormValidation.errorWithMarkup(e.getMessage());
    }
  }
}
