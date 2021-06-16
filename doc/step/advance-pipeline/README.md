# Advance Pipeline Step

[cloud-manager-events]: https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/receiving-events.md

This step will advance the Cloud Manager pipeline. That pipeline must be in the proper state based on the actions specified in the step configuration. If the Cloud Manager pipeline is not in a valid state for advancing, then this step will fail the build.

**Note**: *This feature requires that either a [Pipeline Start Trigger](/doc/trigger/start-pipeline/README.md) or a [Pipeline Start Builder](/doc/builder/start-pipeline/README.md) is defined earlier in the pipeline.*

## Usage

Syntax:

```
acmAdvancePipeline(
    actions: ['codeQuality', 'approval']
)
```

### Optional Properties

* `actions`: Optional list of [actions][cloud-manager-events] to which this step will respond.
    * An empty list constitutes all actions.
    * At this time only two values are valid: *codeQuality* and *approval* 

## Use Cases

### Advance Pipeline After Pause

The intent of this step is to be used in combination with a `acmPipelineStepState` step where the Cloud Manager Pipeline has paused, and offline actions are necessary before advancing the remote Pipeline.

For example: Wait for the Cloud Manager pipeline to reach the Approval step, allowing for out-of-band operations such as performance or load testing using third party tools. Once those are successful advance the pipeline. 

```
acmPipelineStepState(actions: ['approval'], waitingPause: false)
...
// Do other actions here
...
acmAdvancePipeline(actions: ['approval'])
```
