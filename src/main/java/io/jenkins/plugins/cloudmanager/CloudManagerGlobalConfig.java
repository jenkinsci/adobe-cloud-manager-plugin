package io.jenkins.plugins.cloudmanager;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.util.Optional;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Example of Jenkins global configuration.
 */
@Extension
public class CloudManagerGlobalConfig extends GlobalConfiguration implements AdobeioConfig{

  private String apiKey,organizationID,technicalAccountId;
  private Secret clientSecret, privateKey;


  /** @return the singleton instance */
  public static CloudManagerGlobalConfig get() {
    return GlobalConfiguration.all().get(CloudManagerGlobalConfig.class);
  }

  public CloudManagerGlobalConfig() {
    // When Jenkins is restarted, load any saved configuration from disk.
    load();
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
  public FormValidation doTestConnection(
      @QueryParameter("apiKey") final String apiKey,
      @QueryParameter("organizationID") final String organizationID,
      @QueryParameter("technicalAccountId") final String technicalAccountId,
      @QueryParameter("clientSecret") final Secret clientSecret,
      @QueryParameter("privateKey") final Secret privateKey) {

    // Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    String token =
        CloudManagerAuthUtil.getAccessToken(
            new AdobeioConfig() {
              @Override
              public String getApiKey() {
                return apiKey;
              }

              @Override
              public String getOrganizationID() {
                return organizationID;
              }

              @Override
              public String getTechnicalAccountId() {
                return technicalAccountId;
              }

              @Override
              public Secret getClientSecret() {
                return clientSecret;
              }

              @Override
              public Secret getPrivateKey() {
                return privateKey;
              }
            });

    if (StringUtils.isNoneBlank(token)) {
      return FormValidation.ok("Success! You really know your secrets, eh?");
    } else {
      return FormValidation.error("Fail :(");
    }
  }

}
