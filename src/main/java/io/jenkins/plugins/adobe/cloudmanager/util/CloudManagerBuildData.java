package io.jenkins.plugins.adobe.cloudmanager.util;

/*-
 * #%L
 * Adobe Cloud Manager Plugin
 * %%
 * Copyright (C) 2020 - 2021
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

import java.io.Serializable;

import hudson.model.Action;
import lombok.Data;
import org.kohsuke.stapler.export.ExportedBean;

@Data
@ExportedBean(defaultVisibility = 1500)
public class CloudManagerBuildData implements Action, Serializable, Cloneable {

  private String aioProjectName;
  private String programId;
  private String pipelineId;
  private String executionId;

  public CloudManagerBuildData() {
  }

  public CloudManagerBuildData(String aioProjectName, String programId, String pipelineId, String executionId) {
    this.aioProjectName = aioProjectName;
    this.programId = programId;
    this.pipelineId = pipelineId;
    this.executionId = executionId;
  }

  @Override
  public String getDisplayName() {
    return "Adobe Cloud Manager Build";
  }

  @Override
  public String getIconFileName() {
    return jenkins.model.Jenkins.RESOURCE_PATH + "/plugin/adobe-cloud-manager/icons/Adobe_Experience_Cloud_logo_48px.png";
  }

  @Override
  public String getUrlName() {
    return "adobe-cloud-manager-" + programId + pipelineId + executionId;
  }
}
