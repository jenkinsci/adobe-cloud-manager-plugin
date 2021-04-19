package io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig

import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig

def f = namespace(lib.FormTagLib)
def c = namespace(lib.CredentialsTagLib)

f.entry(title: _("name.title"), field: "name") {
  f.textbox()
}

f.entry(title: _("apiUrl.title"), field: "apiUrl") {
  f.textbox(default: AdobeIOProjectConfig.ADOBE_IO_URL)
}

f.entry(title: _("clientId.title"), field: "clientId") {
  f.textbox()
}

f.entry(title: _("orgId.title"), field: "imsOrganizationId") {
  f.textbox()
}

f.entry(title: _("techAcctId.title"), field: "technicalAccountId") {
  f.textbox()
}

f.entry(title: _("clientSecret.title"), field: "clientSecretCredentialsId") {
  c.select(context:app, includeUser:false, expressionAllowed:false)
}

f.entry(title: _("privateKey.title"), field: "privateKeyCredentialsId") {
  c.select(context:app, includeUser:false, expressionAllowed:false)
}

f.entry(title: _("validateSignatures.title"), field: "validateSignatures") {
  f.checkbox(default: true)
}

f.block() {
  f.validateButton(
      title: _("validate.title"),
      progress: _("progress"),
      method: "verifyCredentials",
      with: "apiUrl,imsOrganizationId,technicalAccountId,clientId,clientSecretCredentialsId,privateKeyCredentialsId"
  )
}
