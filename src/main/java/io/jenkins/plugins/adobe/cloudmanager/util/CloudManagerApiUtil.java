package io.jenkins.plugins.adobe.cloudmanager.util;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudManagerApiUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerApiUtil.class);

  @Nonnull
  public static Function<String, Optional<CloudManagerApi>> createApi() {
    return (projectName) -> {
      AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(projectName);
      if (aioProject != null) {
        Secret token = aioProject.authenticate();
        if (token != null) {
          return Optional.of(CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText()));
        }
      } else {
        LOGGER.error(Messages.CloudManagerApiUtil_error_missingAioProject(projectName));
      }
      return Optional.empty();
    };
  }
}
