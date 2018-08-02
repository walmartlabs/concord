from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

from ansible.plugins.callback import CallbackBase
from ansible.module_utils.six import string_types

import os
import requests
import ujson as json

class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_events'
    CALLBACK_NEEDS_WHITELIST = False

    MAX_STRING_LEN = 1024
    OVERLIMIT_STRING_LEN = MAX_STRING_LEN / 10
    MAX_ARRAY_LEN = 25
    OVERLIMIT_ARRAY_LEN = MAX_ARRAY_LEN / 10

    def __init__(self):
        super(CallbackModule, self).__init__()

        print("Ansible event recording started...")

        baseUrl = os.environ['CONCORD_BASE_URL']
        instanceId = os.environ['CONCORD_INSTANCE_ID']
        sessionToken = os.environ['CONCORD_SESSION_TOKEN']

        self.eventCorrelationId = None
        if "CONCORD_EVENT_CORRELATION_ID" in os.environ:
            self.eventCorrelationId = os.environ['CONCORD_EVENT_CORRELATION_ID']

        self.targetUrl = baseUrl + '/api/v1/process/' + instanceId + '/event'

        s = requests.Session()
        s.headers.update({'X-Concord-SessionToken': sessionToken, 'Content-type': 'application/json'})

        self.session = s

    def handle_event(self, event):
        r = self.session.post(self.targetUrl, data=json.dumps({
            'eventType': 'ANSIBLE',
            'data': dict(event, **{'parentCorrelationId': self.eventCorrelationId})
        }))
        r.raise_for_status()

    def cleanup_results(self, result):
        abridged_result = self._strip_internal_keys(result)

        if 'invocation' in result:
            del abridged_result['invocation']

        if 'diff' in result:
            del abridged_result['diff']

        if 'exception' in abridged_result:
            del abridged_result['exception']

        return self._trunc_long_items(abridged_result)

    def _strip_internal_keys(self, dirty):
        clean = dirty.copy()
        for k in dirty.keys():
            if isinstance(k, string_types) and k.startswith('_ansible_'):
                del clean[k]
            elif isinstance(dirty[k], dict):
                clean[k] = self._strip_internal_keys(dirty[k])
        return clean

    def _trunc_long_items(self, obj):
        if isinstance(obj, basestring):
            overlimit = len(obj) - self.MAX_STRING_LEN
            return (obj[:self.MAX_STRING_LEN] + '...[skipped ' + str(overlimit) +  ' bytes]') if overlimit > self.OVERLIMIT_STRING_LEN else obj
        elif isinstance(obj, list):
            overlimit = len(obj) - self.MAX_ARRAY_LEN
            if overlimit > self.OVERLIMIT_ARRAY_LEN:
                copy = [self._trunc_long_items(o) for o in obj[:self.MAX_ARRAY_LEN]]
                copy.append('[skipped ' + str(overlimit) + ' lines]')
                return copy
            else:
                return [self._trunc_long_items(o) for o in obj]
        elif isinstance(obj, tuple):
            return tuple(self._trunc_long_items(o) for o in obj)
        elif isinstance(obj, dict):
            return dict((k, self._trunc_long_items(v)) for (k,v) in obj.items())
        else:
            return obj

    def _on_task_start(self, host, task):
        data = {
            'status': "RUNNING",
            'playbook': self.playbook._file_name,
            'host': host.name,
            'hostGroup': self.play.get_name(),
            'task': task.get_name(),
            'correlationId': host.name + task._uuid,
            'phase': "pre"
        }

        self.handle_event(data)

    def _on_task_skipped(self, result):
        data = {
            'status': 'SKIPPED',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'correlationId': result._host.name + result._task._uuid,
            'phase': "post",
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    ### Ansible callbacks ###

    def v2_playbook_on_start(self, playbook):
        self.playbook = playbook
        self.playbook_on_start()

    def v2_runner_on_failed(self, result, **kwargs):
        data = {
            'status': "FAILED",
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'correlationId': result._host.name + result._task._uuid,
            'phase': "post",
            'result': self.cleanup_results(result._result),
            'ignore_errors': result._task_fields['ignore_errors']
        }

        self.handle_event(data)

    def v2_runner_on_ok(self, result, **kwargs):
        data = {
            'status': 'OK',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'correlationId': result._host.name + result._task._uuid,
            'phase': "post",
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_unreachable(self, result):
        data = {
            'status': 'UNREACHABLE',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'correlationId': result._host.name + result._task._uuid,
            'phase': "post",
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_async_failed(self, result, **kwargs):
        data = {
            'status': 'UNREACHABLE',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'correlationId': result._host.name + result._task._uuid,
            'phase': "post",
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def concord_on_task_start(self, host, task):
        self._on_task_start(host, task)

    def v2_runner_on_skipped(self, result):
        self._on_task_skipped(result)

    def v2_runner_item_on_skipped(self, result):
        self._on_task_skipped(result)

    def v2_playbook_on_play_start(self, play):
        self.play = play
