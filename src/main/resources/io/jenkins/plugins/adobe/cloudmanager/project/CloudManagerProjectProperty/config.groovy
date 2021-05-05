package io.jenkins.plugins.adobe.cloudmanager.project.CloudManagerProjectProperty

import io.jenkins.plugins.adobe.cloudmanager.builder.CloudManagerBuilder
import static io.jenkins.plugins.adobe.cloudmanager.project.CloudManagerProjectProperty.DescriptorImpl.ADOBE_CLOUD_MANAGER_PROJECT_BLOCK_NAME

def f = namespace(lib.FormTagLib)
def st = namespace('jelly:stapler')

f.optionalBlock(name: ADOBE_CLOUD_MANAGER_PROJECT_BLOCK_NAME, title: _('title'), checked: instance != null) {
  st.include(page: 'config-aioProject.jelly', class: CloudManagerBuilder.class)
  st.include(page: 'config-program.jelly', class: CloudManagerBuilder.class)
  st.include(page: 'config-pipeline.jelly', class: CloudManagerBuilder.class)
}
