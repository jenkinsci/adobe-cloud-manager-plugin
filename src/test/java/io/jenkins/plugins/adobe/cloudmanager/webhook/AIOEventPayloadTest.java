package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import javax.servlet.ServletInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.CloudManagerEvent;
import io.adobe.cloudmanager.CloudManagerEvent.Type;
import io.adobe.cloudmanager.generated.events.PipelineExecutionStartEvent;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import static org.junit.Assert.*;

public class AIOEventPayloadTest {
  private static final String PARAM_NAME = "payload";

  @Mocked
  private StaplerRequest request;

  @Mocked
  private AIOEventPayload annotation;

  @Tested
  private AIOEventPayload.PayloadHandler handler;

  @Test
  public void invalidContentType() throws Exception {

    new Expectations() {{
      request.getContentType();
      result = ContentType.TEXT_HTML.getMimeType();
    }};

    assertNull(handler.parse(request, annotation, String.class, PARAM_NAME));
  }

  @Test
  public void nullChallengeRequest() throws Exception {
    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_FORM_URLENCODED.getMimeType();
      request.getParameter(AIOEventPayload.PayloadHandler.CHALLENGE_PARAM);
      result = null;
    }};
    assertNull(handler.parse(request, annotation, String.class, PARAM_NAME));
  }
  
  @Test
  public void validChallengeRequest() throws Exception {
    String param = "Challenge Parameter";
    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_FORM_URLENCODED.getMimeType();
      request.getParameter(AIOEventPayload.PayloadHandler.CHALLENGE_PARAM);
      result = param;
    }};
    assertEquals(param, handler.parse(request, annotation, String.class, PARAM_NAME));
  }

  @Test
  public void ioError() throws Exception {

    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_JSON.getMimeType();
      request.getInputStream();
      result = new IOException("Failed");
    }};

    assertNull(handler.parse(request, annotation, String.class, PARAM_NAME));
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

    new MockUp<Type>() {
      @Mock
      public Type from(String source) throws CloudManagerApiException {
        throw new CloudManagerApiException(CloudManagerApiException.ErrorType.PROCESS_EVENT, "failed");
      }
    };

    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_JSON.getMimeType();
      request.getInputStream();
      result = is;
    }};

    assertNull(handler.parse(request, annotation, String.class, PARAM_NAME));
  }

  @Test
  public void invalidClassProvided() throws Exception {
    Class type = AIOEventPayloadTest.class;
    String body = "";
    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new MockUp<Type>() {
      @Mock
      public Class from(String source) throws CloudManagerApiException {
        return null;
      }
    };

    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_JSON.getMimeType();
      request.getInputStream();
      result = null;
    }};

    assertNull(handler.parse(request, annotation, String.class, PARAM_NAME));
  }

  @Test
  public void unableToParseBody() throws Exception {
    Type type = Type.PIPELINE_STARTED;
    String body = "";
    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new MockUp<Type>() {
      @Mock
      public Type from(String source) throws CloudManagerApiException {
        return type;
      }
    };
    new MockUp<CloudManagerEvent>() {
      @Mock
      public <T> T parseEvent(String source, Class<T> type) throws CloudManagerApiException {
        throw new CloudManagerApiException(CloudManagerApiException.ErrorType.PROCESS_EVENT, "Failed");
      }
    };

    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_JSON.getMimeType();
      request.getInputStream();
      result = null;
    }};

    assertNull(handler.parse(request, annotation, String.class, PARAM_NAME));
  }

  @Test
  public void success() throws Exception {
    Type type = Type.PIPELINE_STARTED;
    String body = "";
    new MockUp<IOUtils>() {
      @Mock
      public String toString(InputStream inputStream, Charset charset) {
        return body;
      }
    };

    new MockUp<Type>() {
      @Mock
      public Type from(String source) throws CloudManagerApiException {
        return type;
      }
    };
    new MockUp<CloudManagerEvent>() {
      @Mock
      public PipelineExecutionStartEvent parseEvent(String source, Class<PipelineExecutionStartEvent> type) throws CloudManagerApiException {
        return new PipelineExecutionStartEvent();
      }
    };

    new Expectations() {{
      request.getContentType();
      result = ContentType.APPLICATION_JSON.getMimeType();
      request.getInputStream();
      result = null;
    }};

    assertNotNull(handler.parse(request, annotation, String.class, PARAM_NAME));
  }
}
