package io.jenkins.plugins.cloudmanager;

import hudson.util.Secret;

public interface AdobeioConfig {

  String getApiKey();

  String getOrganizationID();

  String getTechnicalAccountId();

  Secret getClientSecret();

  Secret getPrivateKey();
}
