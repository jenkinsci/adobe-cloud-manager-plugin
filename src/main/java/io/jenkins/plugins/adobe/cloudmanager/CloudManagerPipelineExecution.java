package io.jenkins.plugins.adobe.cloudmanager;

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

import org.apache.commons.lang3.StringUtils;

import io.adobe.cloudmanager.PipelineExecution;
import lombok.Value;

/**
 * Representation of a Cloud Manager Pipeline Execution. Used to reduce repetition logic.
 */
@Value
public class CloudManagerPipelineExecution {
  String programId;
  String pipelineId;
  String executionId;

  public boolean equalTo(PipelineExecution pipelineExecution) {
    return StringUtils.equals(pipelineExecution.getProgramId(), programId) &&
        StringUtils.equals(pipelineExecution.getPipelineId(), pipelineId) &&
        StringUtils.equals(pipelineExecution.getId(), executionId);

  }
}
