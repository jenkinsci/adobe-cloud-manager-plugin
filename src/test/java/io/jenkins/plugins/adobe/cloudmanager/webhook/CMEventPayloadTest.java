package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import javax.servlet.ServletInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.CloudManagerEvent.EventType;
import io.adobe.cloudmanager.event.PipelineExecutionStartEvent;
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
      result = ContentType.APPLICATION_JSON.getMimeType();
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
      result = ContentType.APPLICATION_JSON.getMimeType();
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
      result = ContentType.APPLICATION_JSON.getMimeType();
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
      result = ContentType.APPLICATION_JSON.getMimeType();
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
      result = ContentType.APPLICATION_JSON.getMimeType();
      request.getInputStream();
      result = null;
    }};

    assertNotNull(handler.parse(request, annotation, CMEvent.class, PARAM_NAME));
  }
}
