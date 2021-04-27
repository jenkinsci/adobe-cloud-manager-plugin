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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
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
import io.jenkins.plugins.adobe.cloudmanager.util.CredentialsUtil;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single Adobe IO Project configuration.
 */
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

  public static DomainRequirement getAIODomainRequirement() {
    return new HostnameRequirement(AdobeIOProjectConfig.ADOBE_IO_DOMAIN);
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
  public void setApiUrl(@CheckForNull String apiUrl) {
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
   * Attempts to authenticate to the Adobe IO project and return an Access Token.
   *
   * @return an access token or {@code null} if authentication fails
   */
  @CheckForNull
  public Secret authenticate() {
    try {
      AdobeClientCredentials creds = new AdobeClientCredentials(imsOrganizationId,
          technicalAccountId,
          clientId,
          CredentialsUtil.clientSecretFor(clientSecretCredentialsId).get(),
          AdobeClientCredentials.getKeyFromPem(CredentialsUtil.privateKeyFor(privateKeyCredentialsId).get()));

      if (!isValidToken(creds)) {
        generateNewToken(creds);
      }
      return getToken();
    } catch (NoSuchElementException e) {
      LOGGER.error(Messages.AdobeIOProjectConfig_error_unresolvableCredentials());
    } catch (IOException e) {
      LOGGER.error(Messages.AdobeIOProjectConfig_error_credentialsAccess(e.getLocalizedMessage()));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      LOGGER.error(Messages.AdobeIOProjectConfig_error_privateKeyError(privateKeyCredentialsId));
    } catch (IdentityManagementApiException e) {
      LOGGER.error(Messages.AdobeIOProjectConfig_error_authenticationError(e.getLocalizedMessage()));
    }
    return null;
  }

  private boolean isValidToken(AdobeClientCredentials credentials) throws IdentityManagementApiException {
    Secret token = getToken();
    if (token != null) {
      try {
        return IdentityManagementApi.create(getApiUrl()).isValid(credentials, token.getPlainText());
      } catch (IdentityManagementApiException e) {
        LOGGER.warn(Messages.AdobeIOProjectConfig_warn_checkToken(e.getMessage()));
      }
    }
    return false;
  }

  private void generateNewToken(AdobeClientCredentials credentials) throws IdentityManagementApiException, IOException {
    Secret token = Secret.fromString(IdentityManagementApi.create(apiUrl).authenticate(credentials));

    CredentialsStore store = null;
    Domain domain = null;
    for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
      if (s == null) { continue; }
      domain = s.getDomains().stream().filter(d -> !d.getSpecifications().isEmpty() && d.test(AdobeIOProjectConfig.getAIODomainRequirement())).findFirst().orElse(null);
      store = s;
      if (domain != null) {
        break;
      }
    }
    if (store == null || domain == null) {
      throw new NoSuchElementException(Messages.AdobeIOProjectConfig_error_unresolvableCredentials());
    }

    Optional<StringCredentials> current = CredentialsUtil.aioScopedCredentialsFor(generateCredentialsId(), StringCredentials.class);
    StringCredentials replacement = new StringCredentialsImpl(CredentialsScope.SYSTEM, generateCredentialsId(), Messages.AdobeIOProjectConfig_accessToken_description(getDisplayName()), token);
    if (current.isPresent()) {
      store.updateCredentials(domain, current.get(), replacement);
    } else {
      store.addCredentials(domain, replacement);
    }
  }

  @CheckForNull
  private Secret getToken() {
    return CredentialsUtil.aioScopedCredentialsFor(generateCredentialsId(), StringCredentials.class).map(StringCredentials::getSecret).orElse(null);
  }

  private String generateCredentialsId() {
    return StringUtils.join(new String[]{ getName(), getClientId() }).replaceAll("[^a-zA-Z0-9_.-]+", "");
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

  /**
   * Descriptor for the form to manage the Adobe IO Projects.
   */
  @Extension
  public static class DescriptorImpl extends Descriptor<AdobeIOProjectConfig> {

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.AdobeIOProjectConfig_DescriptorImpl_displayName();
    }

    /**
     * Check that the name is provided.
     *
     * @param name the name of the configuration
     * @return form status
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckName(@QueryParameter String name) {
      if (StringUtils.isBlank(name)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingName());
      }
      return FormValidation.ok();
    }

    /**
     * Check that the client id (aka API Key) is provided.
     *
     * @param clientId the client id
     * @return form status
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckClientId(@QueryParameter String clientId) {
      if (StringUtils.isBlank(clientId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingClientId());
      }
      return FormValidation.ok();
    }

    /**
     * Check that the IMS Org id is provided.
     *
     * @param imsOrganizationId the IMS Org Id
     * @return form status
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckImsOrganizationId(@QueryParameter String imsOrganizationId) {
      if (StringUtils.isBlank(imsOrganizationId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingImsOrg());
      }
      return FormValidation.ok();
    }

    /**
     * Checks if the Technical account Id is provided.
     *
     * @param technicalAccountId the technical account id
     * @return form status
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckTechnicalAccountId(@QueryParameter String technicalAccountId) {
      if (StringUtils.isBlank(technicalAccountId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingTechnicalAccountId());
      }
      return FormValidation.ok();
    }

    /**
     * Checks if the Client Secret credentials id is provided and is of the correct type.
     *
     * @param clientSecretCredentialsId the client secret credentials reference
     * @return the form status
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckClientSecretCredentialsId(@QueryParameter String clientSecretCredentialsId) {
      if (StringUtils.isBlank(clientSecretCredentialsId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingClientSecret());
      }
      Optional<String> clientSecret = CredentialsUtil.clientSecretFor(clientSecretCredentialsId);
      if (!clientSecret.isPresent()) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvableClientSecret(clientSecretCredentialsId));
      }
      return FormValidation.ok();
    }

    /**
     * Checks if the Private Key credential id is provided and of the correct type.
     *
     * @param privateKeyCredentialsId the private key credential reference
     * @return form status
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckPrivateKeyCredentialsId(@QueryParameter String privateKeyCredentialsId) {
      if (StringUtils.isBlank(privateKeyCredentialsId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingPrivateKey());
      }
      Optional<String> privateKey = CredentialsUtil.privateKeyFor(privateKeyCredentialsId);
      if (!privateKey.isPresent()) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvablePrivateKey(privateKeyCredentialsId));
      }
      return FormValidation.ok();
    }

    /**
     * List all of the possible Credentials that can be used for the Client Secret.
     *
     * @param credentialsId the current client secret credential id
     * @return list of credential ids
     */
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

    /**
     * List all of the possible Credentials that can be used for the Private Key.
     *
     * @param credentialsId the current private key credential id
     * @return list of credential ids
     */
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

    /**
     * Verify that the provided information is able to authenticate to Adobe IO.
     *
     * @param apiUrl                    the Adobe IO URL endpoint
     * @param imsOrganizationId         the IMS Org Id
     * @param technicalAccountId        the Technical Account Id
     * @param clientId                  the client id (aka API Key)
     * @param clientSecretCredentialsId the client secret credentials id
     * @param privateKeyCredentialsId   the private key credentials id
     * @return the form validation
     */
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

      Optional<String> clientSecret = CredentialsUtil.clientSecretFor(clientSecretCredentialsId);
      if (!clientSecret.isPresent()) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvableClientSecret(clientSecretCredentialsId));
      }

      Optional<String> privateKey = CredentialsUtil.privateKeyFor(privateKeyCredentialsId);
      if (!privateKey.isPresent()) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvablePrivateKey(privateKeyCredentialsId));
      }

      PrivateKey pk;
      try {
        pk = AdobeClientCredentials.getKeyFromPem(privateKey.get());
      } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvablePrivateKey(privateKeyCredentialsId));
      }
      AdobeClientCredentials creds = new AdobeClientCredentials(imsOrganizationId, technicalAccountId, clientId, clientSecret.get(), pk);

      try {
        IdentityManagementApi.create(apiUrl).authenticate(creds);
        return FormValidation.ok(Messages
            .AdobeIOProjectConfig_DescriptorImpl_validate_credentialsVerified(imsOrganizationId));
      } catch (IdentityManagementApiException e) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_credentialValidationFailed());
      }
    }
  }
}
