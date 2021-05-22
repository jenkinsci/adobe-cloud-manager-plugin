package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStartEvent;
import io.jenkins.plugins.adobe.cloudmanager.trigger.PipelineStartEvent;
import io.jenkins.plugins.adobe.cloudmanager.trigger.PipelineStartTrigger;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;

public class PipelineStartEventSubscriberTest {

  private static String payload;
  private static OffsetDateTime time;

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Tested
  private PipelineStartEventSubscriber tested;

  @Mocked
  private PipelineStartTrigger trigger;

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private PipelineExecution pipelineExecution;

  @BeforeClass
  public static void beforeClass() throws Exception {
    payload = IOUtils.resourceToString("events/pipeline-started.json", Charset.defaultCharset(), PipelineStartEventSubscriberTest.class.getClassLoader());
    time = OffsetDateTime.parse("2021-05-20T22:45:21.090Z");
  }

  @Before
  public void before() {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() {
        return (name) -> Optional.of(api);
      }
    };
  }

  @Test
  public void invalidItemType(@Mocked Item notJob) {
    assertFalse(PipelineStartEventSubscriber.isApplicable().test(notJob));
  }

  @Test
  public void validItemType(@Mocked FreeStyleProject fsp) {
    assertTrue(PipelineStartEventSubscriber.isApplicable().test(fsp));
  }

  @Test
  public void doesNotHaveTrigger() throws Exception {
    FreeStyleProject fsp = rule.createFreeStyleProject();
    assertFalse(PipelineStartEventSubscriber.hasTrigger().test(fsp));
  }

  @Test
  public void hasTrigger() throws Exception {
    FreeStyleProject fsp = rule.createFreeStyleProject();
    fsp.addTrigger(trigger);
    assertTrue(PipelineStartEventSubscriber.hasTrigger().test(fsp));
  }

  @Test
  public void getTrigger() throws Exception {
    FreeStyleProject fsp = rule.createFreeStyleProject();
    fsp.addTrigger(trigger);
    assertNotNull(PipelineStartEventSubscriber.getTrigger().apply(fsp));
  }

  @Test
  public void startsTrigger() {
    PipelineStartEvent pse = new PipelineStartEvent("1", AIO_PROJECT_NAME, "1", "2", "3", time);
    new Expectations() {{
      trigger.onEvent(withEqual(pse));
    }};
    PipelineStartEventSubscriber.start(pse).apply(trigger);
  }

  @Test
  public void onEvent() throws Exception {
    PipelineStartEvent pse = new PipelineStartEvent("1", AIO_PROJECT_NAME, "1", "2", "3", time);
    new Expectations(trigger) {{
      trigger.getAioProject();
      result = AIO_PROJECT_NAME;
      trigger.getProgramId();
      result = "1";
      trigger.getPipelineId();
      result = "2";

      api.getExecution(withInstanceOf(PipelineExecutionStartEvent.class));
      result = pipelineExecution;
      pipelineExecution.getProgramId();
      result = "1";
      pipelineExecution.getPipelineId();
      result = "2";
      pipelineExecution.getId();
      result= "3";

      trigger.onEvent(withEqual(pse));
    }};

    CloudManagerSubscriberEvent cse = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, CloudManagerEvent.EventType.PIPELINE_STARTED, payload);
    FreeStyleProject fsp = rule.createFreeStyleProject();
    fsp.addTrigger(trigger);
    tested.onEvent(cse);
  }
}
