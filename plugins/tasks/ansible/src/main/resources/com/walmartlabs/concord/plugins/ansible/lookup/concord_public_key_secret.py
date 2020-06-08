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
        if argsLen == 1:
            orgName = os.environ['CONCORD_CURRENT_ORG_NAME']
            secretName = terms[0]
        elif argsLen == 2:
            orgName = terms[0]
            secretName = terms[1]
        else:
            raise AnsibleError('Invalid lookup format. Expected: [orgName], secretName')

        concordBaseUrl = os.environ['CONCORD_BASE_URL']
        concordInstanceId = os.environ['CONCORD_INSTANCE_ID']
        concordSessionToken = os.environ['CONCORD_SESSION_TOKEN']

        headers = {'X-Concord-SessionToken': concordSessionToken,
                   'User-Agent': 'ansible (txId: ' + concordInstanceId + ')'}
        url = concordBaseUrl + '/api/v1/org/' + orgName + '/secret/' + secretName + '/public'

        r = requests.get(url, headers=headers)

        if r.status_code != requests.codes.ok:
            resp = self.get_json(r)

            msg = 'Error accessing public key ' + orgName + '/' + secretName + ': '
            if resp:
                try:
                    raise AnsibleError(msg + resp[0]['message'])
                except (IndexError, KeyError, TypeError):
                    pass

            if r.text:
                raise AnsibleError(msg + r.text)

            raise AnsibleError(msg + 'Invalid server response: ' + str(r.status_code))

        data = r.json()
        ret.append(str(data['publicKey']))

        return ret

    def get_json(self, r):
        try:
            return r.json()
        except ValueError:
            # no JSON returned
            return
