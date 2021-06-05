# Pipeline Step State Step

[cloud-manager-events]: https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/receiving-events.md

This step has two states:
 * Pause a pipeline until specific [Pipeline Execution Step Event][cloud-manager-events] has occurred.
 * Pause a pipeline and log each [Pipeline Execution Step Event][cloud-manager-events] that occurs.

**Note**: *This feature requires that the [Adobe IO Webhook](/README.md#adobeio-webhook) is enabled.*

**Note**: *This feature requires that either a [Pipeline Start Trigger](/doc/trigger/start-pipeline/README.md) or a [Pipeline Start Builder](/doc/builder/start-pipeline/README.md) is defined earlier in the pipeline.*

## Usage

Syntax:

```
acmPipelineStepState(
    actions: ['codeQuality', 'securityTest', 'productTest', 'uiTest'],
    advance: true,
    autoApprove: false
)
```

### Optional Properties

* `actions`: Optional list of [actions][cloud-manager-events] to which this step will respond.
  * An empty list constitutes all actions.
* `advance`: Flag to indicate if this step should advance when receiving a *end* action.
* `autoApprove`: Flag to indicate that when a *waiting* event occurs, it should advance the Cloud Manager build.

## Use Cases

### Standalone

This step can be used directly in a pipeline to wait for a specific Cloud Manager build action step. This will effectively pause the pipeline until the event occurs.

```
acmPipelineStepState(actions: ['codeQuality'])
```

### Pipeline End Step Block

This step can be used inside of a [Pipeline End Step](/doc/step/pipeline-end/README.md) block. In this context, the Jenkins pipeline will simply log each event as it arrives, advancing or not. 

```
acamPipelineEnd {
    acmPipelineStepState(advance: false)
}
```
