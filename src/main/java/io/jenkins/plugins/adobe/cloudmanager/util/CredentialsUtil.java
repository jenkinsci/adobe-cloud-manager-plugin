package io.jenkins.plugins.adobe.cloudmanager.util;

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
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import hudson.security.ACL;
import hudson.util.Secret;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.Messages;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Utility class for looking up Credentials for different forms.
 */
public class CredentialsUtil {

  /**
   * Find the Client Secret for the specified credential id.
   *
   * @param credentialsId the id of the credentials
   * @return optional string containing the credentials
   */
  @Nonnull
  public static Optional<String> clientSecretFor(String credentialsId) {
    return aioScopedCredentialsFor(credentialsId, StringCredentials.class).map(StringCredentials::getSecret).map(Secret::getPlainText);
  }

  /**
   * Find the Private Key for the specified credential id. This is returned as the String contents of the provided credential file.
   *
   * @param credentialsId the id of the credentials
   * @return optional String containing the credentials
   */
  @Nonnull
  public static Optional<String> privateKeyFor(String credentialsId) {
    return aioScopedCredentialsFor(credentialsId, FileCredentials.class)
        .map(creds -> {
          try {
            return IOUtils.toString(creds.getContent(), Charset.defaultCharset());
          } catch (IOException e) {
            Messages.AdobeIOProjectConfig_errors_privateKeyError(credentialsId);
            return null;
          }
        });
  }

  /**
   * Look up a Credential object from the stores, using the specified Id.
   *
   * @param credentialsId the id of the credential
   * @param type          the type of the credential to lookup.
   * @param <C>           which type of credentials to lookup.
   * @return optional credential for the provided id
   */
  @Nonnull
  public static <C extends Credentials> Optional<C> credentialsFor(String credentialsId, Class<C> type) {
    return CredentialsMatchers.filter(
        CredentialsProvider.lookupCredentials(type, Jenkins.get(), ACL.SYSTEM, Collections.emptyList()),
        CredentialsMatchers.withId(StringUtils.trimToEmpty(credentialsId))
    ).stream().findFirst();

  }

  /**
   * Look up a Credential object from the stores, using the specified Id. Credentials are limited to the Adobe IO Domain.
   *
   * @param credentialsId the id of the credential
   * @param type          the type of the credential to lookup.
   * @param <C>           which type of credentials to lookup.
   * @return optional credential for the provided id
   */
  @Nonnull
  public static <C extends Credentials> Optional<C> aioScopedCredentialsFor(String credentialsId, Class<C> type) {
    return CredentialsMatchers.filter(
        CredentialsProvider.lookupCredentials(type, Jenkins.get(), ACL.SYSTEM, new HostnameRequirement(AdobeIOProjectConfig.ADOBE_IO_DOMAIN)),
        CredentialsMatchers.withId(StringUtils.trimToEmpty(credentialsId))
    ).stream().findFirst();
  }
}
