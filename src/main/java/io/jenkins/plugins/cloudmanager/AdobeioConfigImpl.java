package io.jenkins.plugins.cloudmanager;

import hudson.util.Secret;

public class AdobeioConfigImpl implements AdobeioConfig {

  private String apiKey, organizationID, technicalAccountId, accessToken;
  private Secret clientSecret, privateKey;

  public AdobeioConfigImpl(
      String apiKey,
      String organizationID,
      String technicalAccountId,
      Secret clientSecret,
      Secret privateKey,
      String accessToken) {
    this.apiKey = apiKey;
    this.organizationID = organizationID;
    this.technicalAccountId = technicalAccountId;
    this.clientSecret = clientSecret;
    this.privateKey = privateKey;
    this.accessToken = accessToken;
  }

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

  @Override
  public String getAccessToken() {
    return accessToken;
  }
}
