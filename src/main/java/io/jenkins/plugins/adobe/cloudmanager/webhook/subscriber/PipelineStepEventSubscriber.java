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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import hudson.Extension;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepWaitingEvent;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;

/**
 * Subscriber for Cloud Manager pipeline execution step events.
 */
@Extension
public class PipelineStepEventSubscriber extends CloudManagerEventSubscriber {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStepEventSubscriber.class);

  private static final Set<CloudManagerEvent.EventType> EVENTS = new HashSet<>(Arrays.asList(STEP_STARTED, STEP_WAITING, STEP_ENDED));

  @Nonnull
  @Override
  protected Set<CloudManagerEvent.EventType> types() {
    return EVENTS;
  }

  /**
   * Calls all {@link io.jenkins.plugins.adobe.cloudmanager.step.PipelineStepStateStep} instances waiting for an event.
   */
  @Override
  protected void onEvent(final CloudManagerSubscriberEvent event) {
    CloudManagerApi api = createApi(event.getAioProjectName());
    if (api == null) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_createApi());
      return;
    }
    try {

      final PipelineExecutionStepState stepState;
      switch (event.getType()) {
        case STEP_STARTED:
          stepState = api.getExecutionStepState(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionStepStartEvent.class));
          break;
        case STEP_WAITING:
          stepState = api.getExecutionStepState(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionStepWaitingEvent.class));
          break;
        case STEP_ENDED:
          stepState = api.getExecutionStepState(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionStepEndEvent.class));
          break;
        default:
          LOGGER.warn(Messages.PipelineStepEventSubscriber_warn_invalidStepState(event.getType()));
          return;
      }

      final PipelineExecution pipelineExecution = stepState.getExecution();
      StepExecution.applyAll(PipelineStepStateExecution.class, (execution) -> {
        try {
          if (execution.isApplicable(stepState) && execution.isApplicable(pipelineExecution)) {
            if (event.getType() == STEP_STARTED || event.getType() == STEP_ENDED) {
              execution.occurred(pipelineExecution, stepState);
            } else {
              execution.waiting(pipelineExecution, stepState);
            }
          }
        } catch (IOException | InterruptedException | TimeoutException ex) {
          LOGGER.error(Messages.CloudManagerEventSubscriber_error_notifyExecution(ex.getLocalizedMessage()));
        }
        return null;
      });
    } catch (CloudManagerApiException e) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_api(e.getLocalizedMessage()));
    }
  }
}
