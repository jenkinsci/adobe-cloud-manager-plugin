package io.jenkins.plugins.adobe.cloudmanager.util;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import hudson.util.ListBoxModel;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineUpdate;
import io.adobe.cloudmanager.Program;
import io.adobe.cloudmanager.Variable;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class DescriptorHelperTest {

  private static final String PROGRAM_ID = "2";
  private static final String PIPELINE_ID = "4";
  private static final List<Program> programs = new ArrayList<>();
  private static final List<Pipeline> pipelines = new ArrayList<>();
  @Injectable
  private final String aioProject = AIO_PROJECT_NAME;
  @Injectable
  private final String program = "Program Name";
  @Injectable
  private final String pipeline = "Pipeline Name";
  @Mocked
  private AdobeIOConfig aioConfig;
  @Mocked
  private AdobeIOProjectConfig adobeIOProjectConfig;

  @Mocked
  private CloudManagerApi api;

  @BeforeClass
  public static void beforeClass() {
    programs.add(new ProgramImpl("1", "Another Program"));
    programs.add(new ProgramImpl(PROGRAM_ID, "Program Name"));
    pipelines.add(new PipelineImpl(PIPELINE_ID, "Pipeline Name"));
  }

  @Before
  public void before() {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() { return (name) -> Optional.of(api); }
    };
  }


  @Test
  public void fillAioProjectItemsEmpty() {
    new Expectations() {{
      AdobeIOConfig.configuration();
      result = aioConfig;
      aioConfig.getProjectConfigs();
      result = Collections.emptyList();
    }};

    ListBoxModel lbm = DescriptorHelper.fillAioProjectItems();
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillAioProjectItems() {
    final String displayName = "Display Name";
    final String name = "Name";
    new Expectations() {{
      AdobeIOConfig.configuration();
      result = aioConfig;
      aioConfig.getProjectConfigs();
      result = Collections.singletonList(adobeIOProjectConfig);
      adobeIOProjectConfig.getDisplayName();
      result = displayName;
      adobeIOProjectConfig.getName();
      result = name;
    }};

    ListBoxModel lbm = DescriptorHelper.fillAioProjectItems();
    assertEquals(2, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
    assertEquals(displayName, lbm.get(1).name);
    assertEquals(name, lbm.get(1).value);
  }

  @Test
  public void fillProgramItemsBlankAioProject() {
    ListBoxModel lbm = DescriptorHelper.fillProgramItems("");
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillProgramItemsApiCreationFailed() {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() { return (name) -> Optional.empty(); }
    };

    ListBoxModel lbm = DescriptorHelper.fillProgramItems(aioProject);
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillProgramItemsApiFailed() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, PROGRAM_ID);
    }};

    ListBoxModel lbm = DescriptorHelper.fillProgramItems(aioProject);
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillProgramItemsSuccess() throws Exception {
    new Expectations() {{
      api.listPrograms();
      result = programs;
    }};

    ListBoxModel lbm = DescriptorHelper.fillProgramItems(aioProject);
    assertEquals(1 + programs.size(), lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
    lbm.subList(0, 1).clear();
    assertEquals(programs.size(), lbm.size());
    for (int i = 0; i < lbm.size(); i++) {
      assertEquals(programs.get(i).getName(), lbm.get(i).name);
      assertEquals(programs.get(i).getId(), lbm.get(i).value);
    }
  }

  @Test
  public void fillPipelineItemsBlankAioProject() {
    ListBoxModel lbm = DescriptorHelper.fillPipelineItems("", "");
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillPipelineItemsBlankProgram() {
    ListBoxModel lbm = DescriptorHelper.fillPipelineItems(aioProject, "");
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillPipelineItemsApiCreationFailed() {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() { return (name) -> Optional.empty(); }
    };

    ListBoxModel lbm = DescriptorHelper.fillPipelineItems(aioProject, PROGRAM_ID);
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillPipelineItemsApiFailed() throws Exception {
    new Expectations() {{
      api.listPipelines(PROGRAM_ID);
      result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, PROGRAM_ID);
    }};

    ListBoxModel lbm = DescriptorHelper.fillPipelineItems(aioProject, PROGRAM_ID);
    assertEquals(1, lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
  }

  @Test
  public void fillPipelineItemsSuccess() throws Exception {
    new Expectations() {{
      api.listPipelines(PROGRAM_ID);
      result = pipelines;
    }};

    ListBoxModel lbm = DescriptorHelper.fillPipelineItems(aioProject, PROGRAM_ID);
    assertEquals(1 + pipelines.size(), lbm.size());
    assertEquals(Messages.DescriptorHelper_defaultListItem(), lbm.get(0).name);
    lbm.subList(0, 1).clear();
    assertEquals(pipelines.size(), lbm.size());
    for (int i = 0; i < lbm.size(); i++) {
      assertEquals(pipelines.get(i).getName(), lbm.get(i).name);
      assertEquals(pipelines.get(i).getId(), lbm.get(i).value);
    }
  }

  public static class ProgramImpl implements Program {

    private final String id;
    private final String name;

    public ProgramImpl(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getSelfLink() {
      return null;
    }

    @Override
    public void delete() throws CloudManagerApiException {

    }
  }

  public static class PipelineImpl implements Pipeline {

    private final String id;
    private final String name;

    public PipelineImpl(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getProgramId() {
      return null;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Status getStatusState() {
      return null;
    }

    @Override
    public PipelineExecution startExecution() throws CloudManagerApiException {
      return null;
    }

    @Override
    public PipelineExecution getExecution(String executionId) throws CloudManagerApiException {
      return null;
    }

    @Override
    public Pipeline update(PipelineUpdate update) throws CloudManagerApiException {
      return null;
    }

    @Override
    public void delete() throws CloudManagerApiException {

    }

    @Override
    public Set<Variable> listVariables() throws CloudManagerApiException {
      return null;
    }

    @Override
    public Set<Variable> setVariables(Variable... variables) throws CloudManagerApiException {
      return null;
    }

    @Override
    public String getSelfLink() {
      return null;
    }
  }
}
