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

import java.util.Collection;
import java.util.Collections;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.Program;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for generating builder's UI selection lists.
 */
public class DescriptorHelper {

  public static final Logger LOGGER = LoggerFactory.getLogger(DescriptorHelper.class);

  @CheckForNull
  public static CloudManagerApi createApi(@Nonnull String aioProject) {
    AdobeIOProjectConfig cfg = AdobeIOConfig.projectConfigFor(aioProject);

    if (cfg == null) {
      return null;
    }
    Secret token = cfg.authenticate();
    if (token == null) {
      return null;
    }
    return CloudManagerApi.create(cfg.getImsOrganizationId(), cfg.getClientId(), token.getPlainText());
  }

  @Nonnull
  public static ListBoxModel fillAioProjectItems() {
    ListBoxModel lbm = new ListBoxModel();
    lbm.add(Messages.DescriptorHelper_defaultListItem(), "");
    AdobeIOConfig aio = AdobeIOConfig.configuration();
    for (AdobeIOProjectConfig cfg : aio.getProjectConfigs()) {
      lbm.add(cfg.getDisplayName(), cfg.getName());
    }
    return lbm;
  }

  @Nonnull
  public static ListBoxModel fillProgramItems(String aioProject) {
    ListBoxModel lbm = new ListBoxModel();
    lbm.add(Messages.DescriptorHelper_defaultListItem(), "");
    try {
      if (StringUtils.isNotBlank(aioProject)) {
        CloudManagerApi api = createApi(aioProject);
        Collection<Program> programs = api == null ? Collections.emptyList() : api.listPrograms();
        for (Program p : programs) {
          lbm.add(p.getName(), p.getId());
        }
      }
    } catch (CloudManagerApiException e) {
      LOGGER.error(Messages.DescriptorHelper_error_CloudManagerApiException(e.getLocalizedMessage()));
    }
    return lbm;
  }

  @Nonnull
  public static ListBoxModel fillPipelineItems(String aioProject, String program) {
    ListBoxModel lbm = new ListBoxModel();
    lbm.add(Messages.DescriptorHelper_defaultListItem(), "");

    if (StringUtils.isNotBlank(aioProject) && StringUtils.isNotBlank(program)) {
      CloudManagerApi api = createApi(aioProject);
      try {
        Collection<Pipeline> pipelines = api == null ? Collections.emptyList() : api.listPipelines(program);
        for (Pipeline p : pipelines) {
          lbm.add(p.getName(), p.getId());
        }
      } catch (CloudManagerApiException e) {
        LOGGER.error(Messages.DescriptorHelper_error_CloudManagerApiException(e.getLocalizedMessage()));
      }
    }
    return lbm;
  }
}
