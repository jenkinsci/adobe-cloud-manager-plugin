package io.jenkins.plugins.adobe.cloudmanager.action;

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

import io.adobe.cloudmanager.StepAction;
import lombok.Data;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Action for storing the decision made by a user on a Cloud Manager build step.
 */
@Data
@ExportedBean(defaultVisibility = 1510)
public class PipelineStepDecisionAction implements PersistentAction {

  private final String userId;
  private final StepAction step;
  private final Decision decision;

  public PipelineStepDecisionAction(String userId, StepAction step, Decision decision) {
    this.userId = userId;
    this.step = step;
    this.decision = decision;
  }

  @Override
  public String getDisplayName() {
    return Messages.PipelineStepDecisionAction_displayName(userId, decision, step);
  }

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return null;  // Returning null keeps it off the left nav.
  }

  /**
   * Options for a decision.
   */
  public enum Decision {
    APPROVED, REJECTED
  }
}
