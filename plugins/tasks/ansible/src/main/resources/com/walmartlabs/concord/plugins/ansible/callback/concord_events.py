from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

from ansible.plugins.callback import CallbackBase
from ansible.module_utils.six import string_types

import os
import time
import ujson as json
import grpc
import time
import json

from google.protobuf import timestamp_pb2

import server_pb2
import server_pb2_grpc

class CallbackModule(CallbackBase):

    def __init__(self):
        super(CallbackModule, self).__init__()

        print("Ansible event recording started...")

        self.playbook = None
        concord_host = os.environ['CONCORD_HOST']
        concord_port = os.environ['CONCORD_PORT']
        self.instanceId = os.environ['CONCORD_INSTANCE_ID']

        self.channel = grpc.insecure_channel(concord_host + ':' + concord_port)
        self.service = server_pb2_grpc.TEventServiceStub(self.channel)

    def handle_event(self, event):
        # print("---> event: {}".format(json.dumps(event)))

        now = time.time()
        seconds = int(now)
        nanos = int((now - seconds) * 10**9)
        timestamp = timestamp_pb2.Timestamp(seconds=seconds, nanos=nanos)

        self.service.onEvent(server_pb2.TEventRequest(
            instanceId=self.instanceId,
            type=1,
            date=timestamp,
            data=json.dumps(event)))

    def cleanup_results(self, result):
        abridged_result = self._strip_internal_keys(result)

        if 'invocation' in result:
            del abridged_result['invocation']

        if 'diff' in result:
            del abridged_result['diff']

        if 'exception' in abridged_result:
            del abridged_result['exception']

        return abridged_result

    def _strip_internal_keys(self, dirty):
        clean = dirty.copy()
        for k in dirty.keys():
            if isinstance(k, string_types) and k.startswith('_ansible_'):
                del clean[k]
            elif isinstance(dirty[k], dict):
                clean[k] = self._strip_internal_keys(dirty[k])
        return clean

    ### Ansible callbacks ###

    def v2_playbook_on_start(self, playbook):
        self.playbook = playbook
        self.playbook_on_start()

    def v2_runner_on_failed(self, result, **kwargs):
        data = {
            'status': "FAILED",
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'task': result._task.get_name(),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_ok(self, result, **kwargs):
        data = {
            'status': "OK",
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'task': result._task.get_name(),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_unreachable(self, result):
        data = {
            'status': "UNREACHABLE",
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'task': result._task.get_name(),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_async_failed(self, result, **kwargs):
        data = {
            'status': "UNREACHABLE",
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'task': result._task.get_name(),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)
