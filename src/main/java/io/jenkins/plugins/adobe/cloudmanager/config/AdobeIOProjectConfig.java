package io.jenkins.plugins.adobe.cloudmanager.config;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.adobe.cloudmanager.AdobeClientCredentials;
import io.adobe.cloudmanager.IdentityManagementApi;
import io.adobe.cloudmanager.IdentityManagementApiException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdobeIOProjectConfig extends AbstractDescribableImpl<AdobeIOProjectConfig> {

  /**
   * For filtering out Credentials for the dropdown.
   */
  public static final String ADOBE_IO_DOMAIN = "ims-na1.adobelogin.com";

  public static final String ADOBE_IO_URL = "https://" + ADOBE_IO_DOMAIN;

  private static final Logger LOGGER = LoggerFactory.getLogger(AdobeIOProjectConfig.class);

  @CheckForNull
  private String name;

  private String apiUrl = ADOBE_IO_URL;

  @CheckForNull
  private String clientId;

  @CheckForNull
  private String imsOrganizationId;

  @CheckForNull
  private String technicalAccountId;

  @CheckForNull
  private String clientSecretCredentialsId;

  @CheckForNull
  private String privateKeyCredentialsId;

  private boolean validateSignatures = true;

  @DataBoundConstructor
  public AdobeIOProjectConfig() {

  }

  @Nonnull
  public static Optional<String> clientSecretFor(String credentialsId) {
    return credentialsFor(credentialsId, StringCredentials.class)
        .map(StringCredentials::getSecret).map(Secret::getPlainText);
  }

  @Nonnull
  public static Optional<String> privateKeyFor(String credentialsId) {
    return credentialsFor(credentialsId, FileCredentials.class)
        .map(creds -> {
          try {
            return IOUtils.toString(creds.getContent(), Charset.defaultCharset());
          } catch (IOException e) {
            Messages.AdobeIOProjectConfig_errors_privateKeyError(credentialsId);
            return null;
          }
        });
  }

  @Nonnull
  public static <C extends Credentials> Optional<C> credentialsFor(String credentialsId, Class<C> type) {
    return CredentialsMatchers.filter(
        CredentialsProvider.lookupCredentials(type, Jenkins.get(), ACL.SYSTEM, new HostnameRequirement(ADOBE_IO_DOMAIN)),
        CredentialsMatchers.withId(StringUtils.trimToEmpty(credentialsId))
    ).stream().findFirst();
  }

  public String getName() {
    return name;
  }

  @DataBoundSetter
  public void setName(@CheckForNull String name) {
    this.name = name;
  }

  public String getApiUrl() {
    return apiUrl;
  }

  @DataBoundSetter
  public void setApiUrl(String apiUrl) {
    this.apiUrl = StringUtils.defaultIfBlank(apiUrl, ADOBE_IO_URL);
  }

  public String getClientId() {
    return clientId;
  }

  @DataBoundSetter
  public void setClientId(@CheckForNull String clientId) {
    this.clientId = clientId;
  }

  public String getImsOrganizationId() {
    return imsOrganizationId;
  }

  @DataBoundSetter
  public void setImsOrganizationId(@CheckForNull String imsOrganizationId) {
    this.imsOrganizationId = imsOrganizationId;
  }

  public String getTechnicalAccountId() {
    return technicalAccountId;
  }

  @DataBoundSetter
  public void setTechnicalAccountId(@CheckForNull String technicalAccountId) {
    this.technicalAccountId = technicalAccountId;
  }

  public String getClientSecretCredentialsId() {
    return clientSecretCredentialsId;
  }

  @DataBoundSetter
  public void setClientSecretCredentialsId(@CheckForNull String clientSecretCredentialsId) {
    this.clientSecretCredentialsId = clientSecretCredentialsId;
  }

  public String getPrivateKeyCredentialsId() {
    return privateKeyCredentialsId;
  }

  @DataBoundSetter
  public void setPrivateKeyCredentialsId(@CheckForNull String privateKeyCredentialsId) {
    this.privateKeyCredentialsId = privateKeyCredentialsId;
  }

  public boolean isValidateSignatures() {
    return validateSignatures;
  }

  @DataBoundSetter
  public void setValidateSignatures(boolean validateSignatures) {
    this.validateSignatures = validateSignatures;
  }

  /**
   * Returns the display name, the configured name and the IMS Org Id.
   *
   * @return formatted display name
   */
  @Nonnull
  public String getDisplayName() {
    return Messages.AdobeIOProjectConfig_displayName(getName(), getImsOrganizationId());
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<AdobeIOProjectConfig> {

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.AdobeIOProjectConfig_DescriptorImpl_displayName();
    }

    @SuppressWarnings("unused")
    public FormValidation doCheckName(@QueryParameter String name) {
      if (StringUtils.isBlank(name)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_missingName());
      }
      return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    public FormValidation doCheckClientId(@QueryParameter String clientId) {
      if (StringUtils.isBlank(clientId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_missingClientId());
      }
      return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    public FormValidation doCheckImsOrganizationId(@QueryParameter String imsOrganizationId) {
      if (StringUtils.isBlank(imsOrganizationId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_missingImsOrg());
      }
      return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    public FormValidation doCheckTechnicalAccountId(@QueryParameter String technicalAccountId) {
      if (StringUtils.isBlank(technicalAccountId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_missingTechnicalAccountId());
      }
      return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    public FormValidation doCheckClientSecretCredentialsId(@QueryParameter String clientSecretCredentialsId) {
      if (StringUtils.isBlank(clientSecretCredentialsId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_missingClientSecret());
      }
      Optional<String> clientSecret = clientSecretFor(clientSecretCredentialsId);
      if (!clientSecret.isPresent()) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_unresolvableClientSecret(clientSecretCredentialsId));
      }
      return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    public FormValidation doCheckPrivateKeyCredentialsId(@QueryParameter String privateKeyCredentialsId) {
      if (StringUtils.isBlank(privateKeyCredentialsId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_missingPrivateKey());
      }
      Optional<String> privateKey = privateKeyFor(privateKeyCredentialsId);
      if (!privateKey.isPresent()) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_errors_unresolvablePrivateKey(privateKeyCredentialsId));
      }
      return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    public ListBoxModel doFillClientSecretCredentialsIdItems(@QueryParameter String credentialsId) {
      if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
        return new StandardListBoxModel().includeCurrentValue(credentialsId);
      }

      return new StandardListBoxModel()
          .includeEmptyValue()
          .includeMatchingAs(ACL.SYSTEM,
              Jenkins.get(),
              StringCredentials.class,
              URIRequirementBuilder.fromUri(ADOBE_IO_DOMAIN).build(),
              CredentialsMatchers.always());
    }

    @SuppressWarnings("unused")
    public ListBoxModel doFillPrivateKeyCredentialsIdItems(@QueryParameter String credentialsId) {
      if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
        return new StandardListBoxModel().includeCurrentValue(credentialsId);
      }

      return new StandardListBoxModel()
          .includeEmptyValue()
          .includeMatchingAs(ACL.SYSTEM,
              Jenkins.get(),
              FileCredentials.class,
              URIRequirementBuilder.fromUri(ADOBE_IO_DOMAIN).build(),
              CredentialsMatchers.always());
    }

    @RequirePOST
    @Restricted(DoNotUse.class)
    @SuppressWarnings("unused")
    public FormValidation doVerifyCredentials(
        @QueryParameter String apiUrl,
        @QueryParameter String imsOrganizationId,
        @QueryParameter String technicalAccountId,
        @QueryParameter String clientId,
        @QueryParameter String clientSecretCredentialsId,
        @QueryParameter String privateKeyCredentialsId) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);

      Optional<String> clientSecret = clientSecretFor(clientSecretCredentialsId);
      if (!clientSecret.isPresent()) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_errors_unresolvableClientSecret(clientSecretCredentialsId));
      }

      Optional<String> privateKey = privateKeyFor(privateKeyCredentialsId);
      if (!privateKey.isPresent()) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_errors_unresolvablePrivateKey(privateKeyCredentialsId));
      }

      PrivateKey pk;
      try {
        pk = AdobeClientCredentials.getKeyFromPem(privateKey.get());
      } catch (Exception e) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_errors_unresolvablePrivateKey(privateKeyCredentialsId));
      }
      AdobeClientCredentials creds = new AdobeClientCredentials(imsOrganizationId, technicalAccountId, clientId, clientSecret.get(), pk);

      try {
        IdentityManagementApi.create(apiUrl).authenticate(creds);
        return FormValidation.ok(Messages
            .AdobeIOProjectConfig_DescriptorImpl_validate_credentialsVerified(imsOrganizationId));
      } catch (IdentityManagementApiException e) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_errors_credentialValidationFailed());
      }
    }
  }
}
