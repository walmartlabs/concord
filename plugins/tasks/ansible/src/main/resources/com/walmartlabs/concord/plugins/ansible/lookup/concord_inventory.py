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

        argsLen = len(terms)
        if argsLen < 2:
            raise AnsibleError('Invalid lookup format. Expected: orgName, inventoryName, queryName')
        elif argsLen <= 3:
            orgName = os.environ['CONCORD_CURRENT_ORG_NAME']
            inventoryName = terms[0]
            queryName, resultParams = _parse_parameters(terms[1])
            queryParams = terms[2]
        else:
            orgName = terms[0]
            inventoryName = terms[1]
            queryName, resultParams = _parse_parameters(terms[2])
            queryParams = terms[3]

        concordBaseUrl = os.environ['CONCORD_BASE_URL']
        concordInstanceId = os.environ['CONCORD_INSTANCE_ID']
        concordSessionToken = os.environ['CONCORD_SESSION_TOKEN']

        headers = {'X-Concord-SessionToken': concordSessionToken,
                   'Content-type': 'application/json',
                   'User-Agent': 'ansible (txId: ' + concordInstanceId + ')'}
        url = concordBaseUrl + '/api/v1/org/' + orgName + '/inventory/' + inventoryName + '/query/' + queryName + "/exec"

        r = requests.post(url, headers=headers, data=json.dumps(queryParams))

        if r.status_code != requests.codes.ok:
            raise AnsibleError('Invalid server response: ' + str(r.status_code))

        resultElement = resultParams['result']
        if resultElement != -1:
            ret.append(r.json()[0])
        else:
            ret.append(r.json())

        return ret
