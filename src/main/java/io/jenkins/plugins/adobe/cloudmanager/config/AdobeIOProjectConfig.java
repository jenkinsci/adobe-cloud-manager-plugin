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
import java.util.ArrayList;
import java.util.List;
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
 * <p>
 *   Provides wrapper/convenience for interactions with IMS's API.
 * </p>
 */
public class AdobeIOProjectConfig extends AbstractDescribableImpl<AdobeIOProjectConfig> {

  /**
   * For filtering out Credentials for the dropdown. Credentials will only be listed if they match this domain restriction.
   */
  public static final String ADOBE_IO_DOMAIN = "ims-na1.adobelogin.com";

  /**
   * Default IMS API endpoint.
   */
  public static final String ADOBE_IO_URL = "https://" + ADOBE_IO_DOMAIN;

  private static final Logger LOGGER = LoggerFactory.getLogger(AdobeIOProjectConfig.class);

  private String name;
  private String apiUrl = ADOBE_IO_URL;
  private String clientId;
  private String imsOrganizationId;
  private String technicalAccountId;
  private String clientSecretCredentialsId;
  private String privateKeyCredentialsId;

  @DataBoundConstructor
  public AdobeIOProjectConfig() {
  }

  public static DomainRequirement getAIODomainRequirement() {
    return new HostnameRequirement(AdobeIOProjectConfig.ADOBE_IO_DOMAIN);
  }

  @CheckForNull
  public String getName() {
    return name;
  }

  @DataBoundSetter
  public void setName(String name) {
    this.name = name;
  }

  @CheckForNull
  public String getApiUrl() {
    return apiUrl;
  }

  @DataBoundSetter
  public void setApiUrl(String apiUrl) {
    this.apiUrl = StringUtils.defaultIfBlank(apiUrl, ADOBE_IO_URL);
  }

  @CheckForNull
  public String getClientId() {
    return clientId;
  }

  @DataBoundSetter
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  @CheckForNull
  public String getImsOrganizationId() {
    return imsOrganizationId;
  }

  @DataBoundSetter
  public void setImsOrganizationId(String imsOrganizationId) {
    this.imsOrganizationId = imsOrganizationId;
  }

  @CheckForNull
  public String getTechnicalAccountId() {
    return technicalAccountId;
  }

  @DataBoundSetter
  public void setTechnicalAccountId(String technicalAccountId) {
    this.technicalAccountId = technicalAccountId;
  }

  @CheckForNull
  public String getClientSecretCredentialsId() {
    return clientSecretCredentialsId;
  }

  @DataBoundSetter
  public void setClientSecretCredentialsId(String clientSecretCredentialsId) {
    this.clientSecretCredentialsId = clientSecretCredentialsId;
  }

  @CheckForNull
  public String getPrivateKeyCredentialsId() {
    return privateKeyCredentialsId;
  }

  @DataBoundSetter
  public void setPrivateKeyCredentialsId(String privateKeyCredentialsId) {
    this.privateKeyCredentialsId = privateKeyCredentialsId;
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
          CredentialsUtil.clientSecretFor(clientSecretCredentialsId).get().getPlainText(),
          AdobeClientCredentials.getKeyFromPem(CredentialsUtil.privateKeyFor(privateKeyCredentialsId).get().getPlainText()));

      if (!isValidToken(creds)) {
        generateNewToken(creds);
      }
      return getToken();
    } catch (NoSuchElementException e) {
      LOGGER.error(Messages.AdobeIOProjectConfig_error_authenticate_unresolvableCredentials(clientSecretCredentialsId, privateKeyCredentialsId));
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
      throw new NoSuchElementException(Messages.AdobeIOProjectConfig_error_unresolvableCredentialStore());
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

  @Nonnull
  public String getDisplayName() {
    return Messages.AdobeIOProjectConfig_displayName(getName(), getImsOrganizationId());
  }

  private String generateCredentialsId() {
    return StringUtils.join(new String[]{ getName(), getClientId() }).replaceAll("[^a-zA-Z0-9_.-]+", "");
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<AdobeIOProjectConfig> {

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.AdobeIOProjectConfig_DescriptorImpl_displayName();
    }

    /**
     * Name check - it's required
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckName(@QueryParameter String name) {
      if (StringUtils.isBlank(name)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingName());
      }
      return FormValidation.ok();
    }

    /**
     * Client Id (aka API Key) check - it's required.
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckClientId(@QueryParameter String clientId) {
      if (StringUtils.isBlank(clientId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingClientId());
      }
      return FormValidation.ok();
    }

    /**
     * IMS Org Id check = it's required.
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckImsOrganizationId(@QueryParameter String imsOrganizationId) {
      if (StringUtils.isBlank(imsOrganizationId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingImsOrg());
      }
      return FormValidation.ok();
    }

    /**
     * Technical Account Id check = it's required.
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckTechnicalAccountId(@QueryParameter String technicalAccountId) {
      if (StringUtils.isBlank(technicalAccountId)) {
        return FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingTechnicalAccountId());
      }
      return FormValidation.ok();
    }

    /**
     * Client Secret credential id check - it's required.
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckClientSecretCredentialsId(@QueryParameter String clientSecretCredentialsId) {
      List<FormValidation> validations = new ArrayList<>();
      FormValidation addButtonWarning = FormValidation.warning(Messages.AdobeIOProjectConfig_DescriptorImpl_warn_doNotUseAddCredentialButton());
      validations.add(addButtonWarning);
      if (StringUtils.isBlank(clientSecretCredentialsId)) {
        validations.add(FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingClientSecret()));
        return FormValidation.aggregate(validations);
      }
      Optional<Secret> clientSecret = CredentialsUtil.clientSecretFor(clientSecretCredentialsId);
      if (!clientSecret.isPresent()) {
        validations.add(FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvableClientSecret(clientSecretCredentialsId)));
        return FormValidation.aggregate(validations);
      }
      validations.add(FormValidation.ok());
      return FormValidation.aggregate(validations);
    }

    /**
     * Private Key credential id check - it's required.
     */
    @SuppressWarnings("unused")
    public FormValidation doCheckPrivateKeyCredentialsId(@QueryParameter String privateKeyCredentialsId) {
      List<FormValidation> validations = new ArrayList<>();
      FormValidation addButtonWarning = FormValidation.warning(Messages.AdobeIOProjectConfig_DescriptorImpl_warn_doNotUseAddCredentialButton());
      validations.add(addButtonWarning);

      if (StringUtils.isBlank(privateKeyCredentialsId)) {
        validations.add(FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_missingPrivateKey()));
        return FormValidation.aggregate(validations);
      }
      Optional<Secret> privateKey = CredentialsUtil.privateKeyFor(privateKeyCredentialsId);
      if (!privateKey.isPresent()) {
        validations.add(FormValidation.error(Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvablePrivateKey(privateKeyCredentialsId)));
        return FormValidation.aggregate(validations);
      }
      validations.add(FormValidation.ok());
      return FormValidation.aggregate(validations);
    }

    /**
     * List all of the possible Credentials that can be used for the Client Secret.
     *
     * <p>Client Secrets must be in a domain restricted by {@link #ADOBE_IO_DOMAIN}.</p>
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
     * <p>Private Keys must be in a domain restricted by {@link #ADOBE_IO_DOMAIN}.</p>
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

      Optional<Secret> clientSecret = CredentialsUtil.clientSecretFor(clientSecretCredentialsId);
      if (!clientSecret.isPresent()) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvableClientSecret(clientSecretCredentialsId));
      }

      Optional<Secret> privateKey = CredentialsUtil.privateKeyFor(privateKeyCredentialsId);
      if (!privateKey.isPresent()) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvablePrivateKey(privateKeyCredentialsId));
      }

      PrivateKey pk;
      try {
        pk = AdobeClientCredentials.getKeyFromPem(privateKey.get().getPlainText());
      } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
        return FormValidation.error(
            Messages.AdobeIOProjectConfig_DescriptorImpl_error_unresolvablePrivateKey(privateKeyCredentialsId));
      }
      AdobeClientCredentials creds = new AdobeClientCredentials(imsOrganizationId, technicalAccountId, clientId, clientSecret.get().getPlainText(), pk);

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
