# Pipeline End Step

This step will pause a pipeline execution until a [Pipeline Execution Ended Event](https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/receiving-events.md) is received and processed by the pipeline subscribers, and passed to the executing step. 

**Note**: *This feature requires that the [Adobe IO Webhook](/README.md#adobeio-webhook) is enabled.*

**Note**: *This feature requires that either a [Pipeline Start Trigger](/doc/trigger/start-pipeline/README.md) or a [Pipeline Start Builder](/doc/builder/start-pipeline/README.md) is defined earlier in the pipeline.*

## Usage

Syntax:

```
acmPipelineEnd(mirror: true) {
    // some block
}
```

This step requires a block until [this issue](https://issues.jenkins.io/browse/JENKINS-65646) is resolved.

**Note:** *Do not use this step with an empty block. If you do not provide any mechanism to pause the pipeline inside this step's block, and do not set `emtpy` to true, an infinite waiting loop will occur.*

### Optional Properties

* `mirror`: Flag indicating whether or not to mirror the Cloud Manager state to Jenkins. Default: *true*
  * When enabled, any Cloud Manager build failures or cancellations will cause this pipeline to reflect a `Failed` state.
* `empty`: Flag indicating whether or not the body of this step is empty. Default: *false*


## Enclosed Pipeline Step State Steps

When a [Pipeline Step State Step](/doc/step/pipeline-step-state/README.md) is wrapped by this step, when this step receives an event, it will quietly end the wrapped step. Any other steps contained within are ignored and allowed to finish their operations uninterrupted.





