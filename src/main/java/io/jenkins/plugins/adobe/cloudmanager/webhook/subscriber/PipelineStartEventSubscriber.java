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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStartEvent;
import io.jenkins.plugins.adobe.cloudmanager.trigger.PipelineStartEvent;
import io.jenkins.plugins.adobe.cloudmanager.trigger.PipelineStartTrigger;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;
import static io.jenkins.plugins.adobe.cloudmanager.trigger.PipelineStartTrigger.*;

/**
 * Subscriber for Cloud Manager pipeline execution start events.
 */
@Extension
public class PipelineStartEventSubscriber extends CloudManagerEventSubscriber {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStartEventSubscriber.class);
  private static final Set<CloudManagerEvent.EventType> EVENTS = Collections.singleton(PIPELINE_STARTED);

  protected static Predicate<Item> isApplicable() {
    return (item) -> item instanceof ParameterizedJobMixIn.ParameterizedJob;
  }

  protected static Function<Item, ParameterizedJobMixIn.ParameterizedJob> toJob() {
    return (item) -> (ParameterizedJobMixIn.ParameterizedJob) item;
  }

  protected static Predicate<ParameterizedJobMixIn.ParameterizedJob> hasTrigger() {
    return (job) -> job.getTriggers().values().stream().filter(t -> t instanceof PipelineStartTrigger).findFirst().isPresent();
  }

  protected static Function<ParameterizedJobMixIn.ParameterizedJob, PipelineStartTrigger> getTrigger() {
    return (job) -> {
      Map<TriggerDescriptor, Trigger<?>> triggers = job.getTriggers();
      for (Trigger t : triggers.values()) {
        if (t instanceof PipelineStartTrigger) {
          return (PipelineStartTrigger) t;
        }
      }
      return null;
    };
  }

  protected static Function<PipelineStartTrigger, Void> start(PipelineStartEvent event) {
    return (trigger) -> {
      trigger.onEvent(event);
      return null;
    };
  }

  @Nonnull
  @Override
  protected Set<CloudManagerEvent.EventType> types() {
    return EVENTS;
  }

  /**
   * Calls all {@link io.jenkins.plugins.adobe.cloudmanager.step.PipelineEndStep} instances waiting for an event.
   */
  @Override
  protected void onEvent(CloudManagerSubscriberEvent subscriberEvent) {
    Optional<CloudManagerApi> api = CloudManagerApiUtil.createApi().apply(subscriberEvent.getAioProjectName());
    if (!api.isPresent()) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_createApi());
      return;
    }
    try {
      PipelineExecutionStartEvent startEvent = getPipelineExecutionStartEvent(subscriberEvent);
      final PipelineExecution pe = getPipelineExecution(api.get(), startEvent);
      PipelineStartEvent pse = getPipelineStartEvent(subscriberEvent, startEvent, pe);

      List collection = Jenkins.get().getAllItems().stream()
          .filter(isApplicable())
          .map(toJob())
          .filter(hasTrigger())
          .map(getTrigger())
          .filter(interestedIn(pse))
          .map(start(pse))
          .collect(Collectors.toList());
      LOGGER.debug(Messages.PipelineStartEventSubscriber_debug_notified(collection.size(), pe.getId()));

    } catch (CloudManagerApiException e) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_api(e.getLocalizedMessage()));
    }
  }

  @Nonnull
  private PipelineExecutionStartEvent getPipelineExecutionStartEvent(CloudManagerSubscriberEvent subscriberEvent) throws CloudManagerApiException {
    return CloudManagerEvent.parseEvent(subscriberEvent.getPayload(), PipelineExecutionStartEvent.class);
  }

  @Nonnull
  private PipelineExecution getPipelineExecution(CloudManagerApi api, PipelineExecutionStartEvent startEvent) throws CloudManagerApiException {
    return api.getExecution(startEvent);
  }

  @Nonnull
  private PipelineStartEvent getPipelineStartEvent(CloudManagerSubscriberEvent subscriberEvent, PipelineExecutionStartEvent startEvent, PipelineExecution pe) {
    return new PipelineStartEvent(
        startEvent.getEvent().getAtId(),
        subscriberEvent.getAioProjectName(),
        pe.getProgramId(),
        pe.getPipelineId(),
        pe.getId(),
        startEvent.getEvent().getActivitystreamspublished()
    );
  }
}
