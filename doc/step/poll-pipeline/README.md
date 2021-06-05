# Poll Pipeline Step

This step will poll the Adobe Cloud Manager API, monitoring for a pipeline for completion.

**Note**: *This feature requires that either a [Pipeline Start Trigger](/doc/trigger/start-pipeline/README.md) or a [Pipeline Start Builder](/doc/builder/start-pipeline/README.md) is defined earlier in the pipeline.*

## Usage

Syntax:

```
acmPollPipeline(
    recurrencePeriod: 300000,
    quiet: true
)
```

### Optional Properties

* `recurrencePeriod`: The time to wait between polling events, in milliseconds
  * Default: 5 minutes
  * Minimum: 30 seconds
  * Maximum: 15 minutes
* `quiet`: Flag to indicate whether or not to log each polling event. Default *false*
