/*-
 * #%L
 * Adobe Cloud Manager Plugin
 * %%
 * Copyright (C) 2020 - 2021 Adobe Inc.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig

/*-

MIT License

Copyright (c) 2020 Adobe Inc

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/

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
