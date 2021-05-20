package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

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
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

import hudson.Extension;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionEndEvent;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;

/**
 * Subscriber for Cloud Manager pipeline execution end events.
 */
@Extension
public class PipelineEndEventSubscriber extends CloudManagerEventSubscriber {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineEndEventSubscriber.class);
  private static final Set<CloudManagerEvent.EventType> EVENTS = Collections.singleton(PIPELINE_ENDED);

  @Nonnull
  @Override
  protected Set<CloudManagerEvent.EventType> types() {
    return EVENTS;
  }

  /**
   * Calls all {@link io.jenkins.plugins.adobe.cloudmanager.step.PipelineEndStep} instances waiting for an event.
   */
  @Override
  protected void onEvent(CloudManagerSubscriberEvent event) {
    Optional<CloudManagerApi> api = CloudManagerApiUtil.createApi().apply(event.getAioProjectName());
    if (!api.isPresent()) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_createApi());
      return;
    }
    try {
      final PipelineExecution pe = api.get().getExecution(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionEndEvent.class));
      StepExecution.applyAll(PipelineEndExecution.class, (execution) -> {
        try {
          if (execution.isApplicable(pe)) {
            execution.occurred(pe);
          }
        } catch (IOException | InterruptedException ex) {
          LOGGER.error(Messages.CloudManagerEventSubscriber_error_notifyExecution(ex.getLocalizedMessage()));
        }
        return null;
      });
    } catch (CloudManagerApiException e) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_api(e.getLocalizedMessage()));
    }
  }
}
