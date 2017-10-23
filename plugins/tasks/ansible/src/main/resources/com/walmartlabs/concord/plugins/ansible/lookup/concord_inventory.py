from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

from ansible.errors import AnsibleError
from ansible.plugins.lookup import LookupBase
from ansible.parsing.splitter import parse_kv

import os
import requests
import ujson as json

def _parse_parameters(term):
    first_split = term.split(' ', 1)
    if len(first_split) <= 1:
        # Only a single argument given, therefore it's a queryName
        queryName = term
        params = dict()
    else:
        queryName = first_split[0]
        params = parse_kv(first_split[1])

    params['result'] = int(params.get('result', -1))

    return queryName, params

class LookupModule(LookupBase):

    def run(self, terms, variables, **kwargs):

        ret = []

        if len(terms) < 2:
            raise AnsibleError('Invalid lookup format. Expected: inventoryName, queryName')

        inventoryName = terms[0]
        queryName, resultParams = _parse_parameters(terms[1])
        queryParams = terms[2]

        concordBaseUrl = os.environ['CONCORD_BASEURL']
        concordApiKey = os.environ['CONCORD_APIKEY']

        headers = {'Authorization': concordApiKey, 'Content-type': 'application/json'}
        url = concordBaseUrl + '/api/v1/inventory/' + inventoryName + '/query/' + queryName + "/exec"

        r = requests.post(url, headers=headers, data=json.dumps(queryParams))

        if r.status_code != requests.codes.ok:
            raise AnsibleError('Invalid server respone: ' + str(r.status_code))

        resultElement = resultParams['result']
        if resultElement != -1:
            ret.append(r.json()[0])
        else:
            ret.append(r.json())

        return ret
