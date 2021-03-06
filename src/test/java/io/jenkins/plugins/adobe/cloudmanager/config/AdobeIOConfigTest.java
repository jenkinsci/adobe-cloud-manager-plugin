package io.jenkins.plugins.adobe.cloudmanager.config;

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
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

public class AdobeIOConfigTest {

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Test
  public void configuation() {
    AdobeIOConfig config = AdobeIOConfig.configuration();
    assertNotNull(config);
    assertTrue(config.getProjectConfigs().isEmpty());
  }

  @Test
  public void projectConfigForName() {

    AdobeIOConfig aio = AdobeIOConfig.configuration();

    List<AdobeIOProjectConfig> configs;
    configs = new ArrayList<>();
    AdobeIOProjectConfig found = new AdobeIOProjectConfig();
    found.setName("Adobe IO Project");
    configs.add(found);

    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setName("Another AdobeIO Project");
    configs.add(config);

    aio.setProjectConfigs(configs);

    assertEquals(found, aio.projectConfigFor("Adobe IO Project"));

    assertNull(aio.projectConfigFor("Not Found"));
  }
}
