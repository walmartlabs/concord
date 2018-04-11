from __future__ import (absolute_import, division, print_function)

__metaclass__ = type

from ansible.errors import AnsibleError
from ansible.plugins.lookup import LookupBase

import os
import requests

class LookupModule(LookupBase):

    def run(self, terms, variables, **kwargs):

        ret = []

        argsLen = len(terms)
        if argsLen < 2:
            raise AnsibleError('Invalid lookup format. Expected: [orgName], secretName, storePassword')
        elif argsLen < 3:
            orgName = os.environ['CONCORD_CURRENT_ORG_NAME']
            secretName = terms[0]
            storePassword = terms[1]
        else:
            orgName = terms[0]
            secretName = terms[1]
            storePassword = terms[2]

        concordBaseUrl = os.environ['CONCORD_BASE_URL']
        concordSessionToken = os.environ['CONCORD_SESSION_TOKEN']

        headers = {'X-Concord-SessionToken': concordSessionToken}
        url = concordBaseUrl + '/api/v1/org/' + orgName + '/secret/' + secretName + '/data'

        r = requests.post(url, headers=headers, files=dict(storePassword=storePassword))

        if r.status_code != requests.codes.ok:
            raise AnsibleError('Invalid server response: ' + str(r.status_code))

        ret.append(str(r.content))

        return ret
