package io.jenkins.plugins.adobe.cloudmanager.webhook;

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
import java.io.InputStream;
import java.nio.charset.Charset;
import javax.servlet.ServletInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.CloudManagerEvent.EventType;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import static org.junit.Assert.*;

public class CMEventPayloadTest {
  private static final String PARAM_NAME = "payload";
  private static final String CONTENT_TYPE = "application/json; charset=UTF-8";

  @Mocked
  private StaplerRequest request;

  @Mocked
  private CMEventPayload annotation;

  @Tested
  private CMEventPayload.PayloadHandler handler;

  @Test
  public void invalidContentType() throws Exception {

    new Expectations() {{
      request.getContentType();
      result = ContentType.TEXT_HTML.getMimeType();
    }};

    assertNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }

  @Test
  public void nullChallengeRequest() throws Exception {
    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_FORM_URLENCODED.getMimeType();
      request.getParameter(CMEventPayload.PayloadHandler.CHALLENGE_PARAM);
      result = null;
    }};
    assertNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }
  
  @Test
  public void validChallengeRequest() throws Exception {
    CMEvent event = new CMEvent(null, null, "Challenge Parameter");
    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_FORM_URLENCODED.getMimeType();
      request.getParameter(CMEventPayload.PayloadHandler.CHALLENGE_PARAM);
      result = "Challenge Parameter";
    }};
    assertEquals(event, handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }

  @Test
  public void ioError() throws Exception {

    new Expectations() {{
      request.getContentType();
      result = CONTENT_TYPE;
      request.getInputStream();
      result = new IOException("Failed");
    }};

    assertNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }

  @Test
  public void parseError(@Mocked ServletInputStream is) throws Exception {
    String body = "";

    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new MockUp<EventType>() {
      @Mock
      public EventType from(String source) throws CloudManagerApiException {
        throw new CloudManagerApiException(CloudManagerApiException.ErrorType.PROCESS_EVENT, "failed");
      }
    };

    new Expectations() {{
      request.getContentType();
      result = CONTENT_TYPE;
      request.getInputStream();
      result = is;
    }};

    assertNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }



  @Test
  public void unableToParseBody() throws Exception {
    CloudManagerEvent.EventType type = EventType.PIPELINE_STARTED;
    String body = "Not Json";
    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new Expectations() {{
      request.getContentType();
      result = CONTENT_TYPE;
      request.getInputStream();
      result = null;
    }};

    assertNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }

  @Test
  public void incompleteBody() throws Exception {
    CloudManagerEvent.EventType type = EventType.PIPELINE_STARTED;
    String body = "{}";
    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new MockUp<EventType>() {
      @Mock
      public EventType from(String source) throws CloudManagerApiException {
        return type;
      }
    };

    new Expectations() {{
      request.getContentType();
      result = "application/json; charset=UTF-8";
      request.getInputStream();
      result = null;
    }};

    assertNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }

  @Test
  public void success() throws Exception {
    CloudManagerEvent.EventType type = EventType.PIPELINE_STARTED;
    String body = IOUtils.resourceToString("events/pipeline-ended.json", Charset.defaultCharset(), this.getClass().getClassLoader());
    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new MockUp<EventType>() {
      @Mock
      public EventType from(String source) throws CloudManagerApiException {
        return type;
      }
    };

    new Expectations() {{
      request.getContentType();
      result = CONTENT_TYPE;
      request.getInputStream();
      result = null;
    }};

    assertNotNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }
}
