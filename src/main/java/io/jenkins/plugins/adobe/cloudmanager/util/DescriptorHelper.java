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
import java.util.Optional;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.util.ListBoxModel;
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

  @Nonnull
  public static ListBoxModel fillAioProjectItems() {
    ListBoxModel lbm = new ListBoxModel();
    lbm.add(Messages.DescriptorHelper_defaultListItem(), "");
    AdobeIOConfig aio = AdobeIOConfig.configuration();
    for (AdobeIOProjectConfig cfg : aio.getProjectConfigs()) {
      String name = cfg.getName();
      if (name != null) {
        lbm.add(cfg.getDisplayName(), name);
      }
    }
    return lbm;
  }

  @Nonnull
  public static ListBoxModel fillProgramItems(String aioProject) {
    ListBoxModel lbm = new ListBoxModel();
    lbm.add(Messages.DescriptorHelper_defaultListItem(), "");
    try {
      if (StringUtils.isNotBlank(aioProject)) {
        Optional<CloudManagerApi> api = CloudManagerApiUtil.createApi().apply(aioProject);
        Collection<Program> programs = api.isPresent() ? api.get().listPrograms() : Collections.emptyList();
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
      Optional<CloudManagerApi> api = CloudManagerApiUtil.createApi().apply(aioProject);
      try {
        Collection<Pipeline> pipelines = api.isPresent() ? api.get().listPipelines(program) : Collections.emptyList();
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
