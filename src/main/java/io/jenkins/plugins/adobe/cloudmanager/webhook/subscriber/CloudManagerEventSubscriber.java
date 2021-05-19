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

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract subscriber for Cloud Manager events. Provides standard logic for looking up concrete extensions.
 */
public abstract class CloudManagerEventSubscriber implements ExtensionPoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerEventSubscriber.class);

  /**
   * Lists of all Cloud Manager Event Subscribers
   */
  public static ExtensionList<CloudManagerEventSubscriber> all() {
    return Jenkins.get().getExtensionList(CloudManagerEventSubscriber.class);
  }

  /**
   * Predicate for filtering on streams.
   */
  public static Predicate<CloudManagerEventSubscriber> interested(final CloudManagerEvent.EventType type) {
    return (subscriber) -> subscriber.types().contains(type);
  }


  /**
   * Function for processing events via streams.
   */
  public static Function<CloudManagerEventSubscriber, Void> process(final CloudManagerSubscriberEvent event) {
    return (subscriber) -> {
      subscriber.onEvent(event);
      return null;
    };
  }

  @CheckForNull
  protected CloudManagerApi createApi(String projectName) {
    AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(projectName);
    if (aioProject != null) {
      Secret token = aioProject.authenticate();
      if (token != null) {
        return CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText());
      }
    } else {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_missingAioProject(projectName));
    }
    return null;
  }

  /**
   * List of event types that this subscriber can process.
   */
  @Nonnull
  protected abstract Set<CloudManagerEvent.EventType> types();

  /**
   * Processes the event.
   */
  protected abstract void onEvent(final CloudManagerSubscriberEvent event);

}
