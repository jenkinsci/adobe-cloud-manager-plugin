package io.jenkins.plugins.cloudmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.adobe.cloudmanager.AdobeClientCredentials;
import io.adobe.cloudmanager.IdentityManagementApiException;
import io.adobe.cloudmanager.impl.IdentityManagementApiImpl;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example of Jenkins global configuration.
 */
@Extension
public class CloudManagerGlobalConfig extends GlobalConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerGlobalConfig.class);

  private String organizationID, technicalAccountId;
  private Secret clientSecret, privateKey, apiKey;

  public CloudManagerGlobalConfig() {
    // When Jenkins is restarted, load any saved configuration from disk.
    load();
  }

  /**
   * @return the singleton instance
   */
  public static CloudManagerGlobalConfig get() {
    return GlobalConfiguration.all().get(CloudManagerGlobalConfig.class);
  }

  // GETTERS / SETTERS
  public Secret retrieveAccessToken() {
    Secret token = null;
    try {
      AdobeClientCredentials credentials =
          new AdobeClientCredentials(organizationID, technicalAccountId,
              apiKey.getPlainText(), clientSecret.getPlainText(),
              AdobeClientCredentials.getKeyFromPem(
                  privateKey.getPlainText()
                      .replaceAll("-----BEGIN\\s*\\w*\\s*PRIVATE KEY-----", "")
                      .replaceAll("-----END\\s*\\w*\\s*PRIVATE KEY-----", "")
                      .replaceAll("\\s+", "")
              )
          );
      token = Secret.fromString(new IdentityManagementApiImpl().authenticate(credentials));
    } catch (IdentityManagementApiException e) {
      LOGGER.error("Unable to retrieve Access Token from AdobeIO: {}", e.getMessage());
    } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
      LOGGER.error("Unable to process PrivateKey: {}", e.getMessage());
    }
    return token;
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

    try {
      AdobeClientCredentials credentials =
          new AdobeClientCredentials(organizationID, technicalAccountId,
              apiKey.getPlainText(), clientSecret.getPlainText(),
              AdobeClientCredentials.getKeyFromPem(
                  privateKey.getPlainText()
                      .replaceAll("-----BEGIN\\s*\\w*\\s*PRIVATE KEY-----", "")
                      .replaceAll("-----END\\s*\\w*\\s*PRIVATE KEY-----", "")
                      .replaceAll("\\s+", "")
              )
          );
      String token = new IdentityManagementApiImpl().authenticate(credentials);
      if (StringUtils.isNoneBlank(token)) {
        return FormValidation.okWithMarkup("Success! Save this configuration page.");
      } else {
        return FormValidation.errorWithMarkup(
            "Got a blank access token for some reason, check jenkins logs.");
      }
    } catch (IdentityManagementApiException | IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      LOGGER.error("Unable to get a token", e);
      return FormValidation.errorWithMarkup(e.getMessage());
    }
  }
}
