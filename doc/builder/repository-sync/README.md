# Repository Sync Builder

This builder allows a pipeline to synchronize the current repository of the build process to the specified Cloud Manager Git repository.

## Usage

Syntax:

```
acmRepoSync(
    credentialsId: 'cm-git-credentials',
    url: 'https://git.cloudmanager.adobe.com/dummy/repo/',
    force: true
)
```

### Required properties:

* `url`: The URL of the Cloud Manager repository
* `credentialsId`: The a reference to credentials for Cloud Manager git Authentication.

### Optional Properties

* `force`: Whether or not to force the update. 
  * **NOTE**: *This will overwrite everything in the Cloud Manager repository*

## Notes

### Pipelines

A Git repository does not need to be specified when this step is used in the context of Pipeline as Code project. 

### Multiple Gits

If a project has multiple Git SCMs defined, the first one found will be used as the source repository.

### Missing Source

If the build does not have any Git SCMs configured, this step will fail the build.
