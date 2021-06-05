# Pipeline Start Trigger

This is a Pipeline trigger which will start the Jenkins pipeline when an Adobe IO event is received that corresponds to the configuration. When the configured pipeline is started in Cloud Manager, it will start the *this* Jenkins pipeline.

**Note**: *This feature requires that the [Adobe IO Webhook](/README.md#adobeio-webhook) is enabled.*

## Usage

This configuration is to configure a Pipeline, therefore it is used in the `properties` section.

Syntax

```
properties([
    pipelineTriggers([
        acmPipelineStart(aioProject: 'AIO Project', program: 'Program Name', pipeline: 'Pipeline Name')
    ])
])

```

### Properties

* `aioProject`: The name of the Adobe IO Project configuration as specified in the Jenkins Global settings
* `program`: A reference to the Program context for the Pipeline.
  * This value can be specified as a name or id of the Program
* `pipeline`: The pipeline in the program that will trigger this Jenkins pipeline.
  * This value can be specified as a name or id of the Program
