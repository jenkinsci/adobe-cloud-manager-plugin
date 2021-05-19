# Adobe Cloud Manager Jenkins Plugin

This plugin integrates with [Adobe Cloud Manager API](https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html) to allow a Jenkins job to start a specific pipeline.


## Usage

### Create AdobeIO Service
You must create a **Cloud Manager Service Integration** in your Adobe IO console. [Follow adobe's documentation](https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/create-api-integration.md)

### Configure Plugin with service credentials

After installing the plugin: In your Jenkins instance, 

1. navigate to `Manage Jenkins` > `Configure System` 
2. Scroll down to `Cloud Manager Plugin`
3. Fill in the fields from your created Adobe IO Service Integration (see screenshot below)
4. Click `Test Adobe IO Connection`
5. A success message will display, save the configuration.

<p align="center">
  <br>
  <img src="doc/plugin-global-config.jpg">
  <br>
</p>

### Add Cloud Manager Build Step to a Job

1. Open any Jenkins job
2. Scroll to `Build` section
3. Click `Add Build Step`
4. Select `Cloud Manager Build Step`
5. Select the Program and Pipeline you want this job to start.


<p align="center">
  <br>
  <img src="doc/build-step.gif">
  <br>
</p>

## Issues

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins-ci.org/). The Component is `adobe-cloud-manager-plugin`.

## Contributing

Refer to the general Jenkins. [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

### Code Submissions

Submissions should come as pull request and will be reviewed by project committers. 

### License Headers

Please make sure to run the license header profile before creating a pull request. This can be done with:


> $ mvn clean process-sources process-test-resources -Plicense-header-check

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

