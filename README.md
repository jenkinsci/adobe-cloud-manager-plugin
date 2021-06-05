# Adobe Cloud Manager plugin for Jenkins

[![Build Status](https://ci.jenkins.io/job/Plugins/job/adobe-cloud-manager-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/adobe-cloud-manager-plugin/job/master/)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/adobe-cloud-manager.svg)](https://plugins.jenkins.io/adobe-cloud-manager)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/adobe-cloud-manager-plugin.svg?label=changelog)](https://github.com/jenkinsci/adobe-cloud-manager-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/adobe-cloud-manager.svg?color=blue)](https://plugins.jenkins.io/adobe-cloud-manager)

- [Introduction](#introduction)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Build Actions](#build-actions)
- [Examples](#examples)

## Introduction

This plugin is intended to allow teams to integrate their Adobe Cloud Manager release process with upstream, intermediate and downstream operational tasks. It provides mechanisms for activating builds, responding to AdobeIO Cloud Manager build events, and taking actions during approval steps.

## Versions

This plugin uses automatic releases, which has a different version format. See https://www.jenkins.io/jep/229

## Getting Started

Before using any of the builders or steps in this plugin, Jenkins needs to be configured to authenticate to an Adobe IO project. To do that, follow these steps:

1. [Create an Adobe IO API Integration](https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/create-api-integration.md).
1. [Add the credentials to Jenkins](/doc/config/jenkins-credentials/README.md)
1. [Configure an Adobe IO Project](/doc/config/adobeio-project/README.md)

### AdobeIO WebHook

This plugin supports receiving AdobeIO Cloud Manager events. See the [Adobe IO Project configuration](/doc/adobeio-project/README.md#enable-webhook) for details on the WebHook operation, and the [Adobe IO documentation](https://www.adobe.io/apis/experienceplatform/events/docs.html#!adobedocs/adobeio-events/master/intro/webhooks_intro.md) on configuring a webhook endpoint.

## Usage

The following builders and steps are available in this plugin. See the individual details page on how each functions and how to configure.

All of the items listed here support syntax generation using the in-Jenkins *Pipeline Syntax* tool.

## Triggers

- [Start Pipeline Trigger](/doc/trigger/start-pipeline/README.md)

## Steps/Builders

- [Repository Sync Builder](/doc/builder/repository-sync/README.md)
- [Start Pipeline Builder](/doc/builder/start-pipeline/README.md)
- [Poll Pipeline Step](/doc/step/poll-pipeline/README.md)
- [Pipeline Step Execution Step](/doc/step/pipeline-step-state/README.md)
- [Pipeline End Execution Step](/doc/step/pipeline-end/README.md)

## Build Actions
    
- [Cloud Manager Build](/doc/action/cloud-manager-build/README.md)
- [Pipeline Waiting](/doc/action/pipeline-waiting/README.md)

## Examples

Coming soon.

## Issues

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins-ci.org/). The Component is `adobe-cloud-manager-plugin`.

## Contributing

Refer to the general Jenkins. [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

### Code Submissions

Submissions should come as pull request and will be reviewed by project committers. 

### License Headers

Please make sure to run the license header profile before creating a pull request. This can be done with:

> $ mvn clean process-sources process-test-resources -Plicense-header-check

This will tell you of any files with missing license headers.

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE)
