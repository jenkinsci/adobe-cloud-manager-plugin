# Cloud Manager Pipeline Waiting Action

When a [Pipeline Step State step](/doc/step/pipeline-step-state) receives an event that indicates the remote Cloud Manager pipeline is waiting, a waiting action is added to the current Jenkins build.

This action will allow teams to approve the Cloud Manager build from the Jenkins job.

A link to the approval page is available on the build side panel, when this action has paused a pipeline:

<p align="center">
    <br/>
    <img src="cloud-manager-waiting.png" />
    <br/>
</p>
