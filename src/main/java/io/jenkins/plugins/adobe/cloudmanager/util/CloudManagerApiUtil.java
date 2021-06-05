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

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
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

  /**
   * Get the Program Id for the program by name its name.
   */
  @Nonnull
  public static Optional<String> getProgramId(final CloudManagerApi api, final String programName) {
    try {
      return api.listPrograms()
          .stream()
          .filter(p -> programName.equals(p.getName()))
          .map(p -> p.getId())
          .findFirst();
    } catch (CloudManagerApiException ex) {
      LOGGER.error(Messages.CloudManagerApiUtil_error_CloudManagerApiException(ex.getLocalizedMessage()));
      return Optional.empty();
    }
  }

  /**
   * Get the Program Id for the pipeline by name its name.
   */
  @Nonnull
  public static Optional<String> getPipelineId(final CloudManagerApi api, final String programId, final String pipelineName) {
    try {
      return api.listPipelines(programId, new Pipeline.NamePredicate(pipelineName))
          .stream()
          .map(p -> p.getId())
          .findFirst();
    } catch (CloudManagerApiException ex) {
      LOGGER.error(Messages.CloudManagerApiUtil_error_CloudManagerApiException(ex.getLocalizedMessage()));
      return Optional.empty();
    }
  }
}
