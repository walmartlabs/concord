from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

from ansible.plugins.callback import CallbackBase
from ansible.module_utils.six import string_types

import time
import datetime

import os
import ujson as json

def to_millis(t):
    return int(round(t * 1000))

class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_events'
    CALLBACK_NEEDS_WHITELIST = False

    MAX_STRING_LEN = 1024
    HALF_MAX_STRING_LEN = int(MAX_STRING_LEN / 2)
    OVERLIMIT_STRING_LEN = MAX_STRING_LEN / 10
    MAX_ARRAY_LEN = 26
    HALF_MAX_ARRAY_LENGTH = int(MAX_ARRAY_LEN / 2)
    OVERLIMIT_ARRAY_LEN = MAX_ARRAY_LEN / 10

    def __init__(self):
        super(CallbackModule, self).__init__()

        print("Ansible event recording started...")

        outFilePath = os.environ['CONCORD_ANSIBLE_EVENTS_FILE']
        self.outFile = open(outFilePath, 'a')

        # TODO could be moved into the task itself
        self.currentRetryCount = None
        if "CONCORD_CURRENT_RETRY_COUNT" in os.environ:
            self.currentRetryCount = os.environ['CONCORD_CURRENT_RETRY_COUNT']

        # TODO could be moved into the task itself
        self.eventCorrelationId = None
        if "CONCORD_EVENT_CORRELATION_ID" in os.environ:
            self.eventCorrelationId = os.environ['CONCORD_EVENT_CORRELATION_ID']

        self.taskDurations = {}
        self.hostStatus = {}

    def handle_event(self, event):
        self.outFile.write(json.dumps({
            'eventType': 'ANSIBLE',
            'eventDate': datetime.datetime.utcnow().isoformat() + 'Z',
            'data': dict(event, **{'parentCorrelationId': self.eventCorrelationId,
                                   'currentRetryCount': self.currentRetryCount})
        }))
        self.outFile.write('<~EOL~>\n')
        self.outFile.flush()

    def _record_host_statuses(self):
        for v in self.hostStatus.values():
            event = {
                'hostStatus': "OK",
                'host': v['host'],
                'hostGroup': v['hostGroup']
            }
            self.handle_event(event)

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
            return (obj[:self.HALF_MAX_STRING_LEN] +
                    '...[skipped ' + str(overlimit) + ' bytes]...' +
                    obj[len(obj) - self.HALF_MAX_STRING_LEN:]) \
                if overlimit > self.OVERLIMIT_STRING_LEN else obj
        elif isinstance(obj, list):
            overlimit = len(obj) - self.MAX_ARRAY_LEN
            if overlimit > self.OVERLIMIT_ARRAY_LEN:
                copy = [self._trunc_long_items(o) for o in obj[:self.HALF_MAX_ARRAY_LENGTH]]
                copy.append('[skipped ' + str(overlimit) + ' lines]')
                copy += [self._trunc_long_items(o) for o in obj[len(obj) - self.HALF_MAX_ARRAY_LENGTH:]]
                return copy
            else:
                return [self._trunc_long_items(o) for o in obj]
        elif isinstance(obj, tuple):
            return tuple(self._trunc_long_items(o) for o in obj)
        elif isinstance(obj, dict):
            return dict((k, self._trunc_long_items(v)) for (k,v) in obj.items())
        else:
            return obj

    @staticmethod
    def _host_correlation_id(host_group, host_uuid):
        return host_group + host_uuid

    def _remove_host(self, host):
        host_uuid = host._uuid
        host_group = self.play.get_name()
        correlation_id = self._host_correlation_id(host_group, host_uuid)
        self.hostStatus.pop(correlation_id)

    def _mark_host_running(self, host):
        host_uuid = host._uuid
        host_group = self.play.get_name()
        host_name = host.name
        correlation_id = self._host_correlation_id(host_group, host_uuid)
        self.hostStatus[correlation_id] = {'hostGroup': host_group, 'host': host_name}

    @staticmethod
    def _task_correlation_id(host_name, task_uuid):
        return host_name + task_uuid

    def _task_duration(self, correlation_id):
        if correlation_id in self.taskDurations:
            return to_millis(time.time()) - self.taskDurations.pop(correlation_id)
        return 0

    def _on_task_start(self, host, task):
        task_correlation_id = self._task_correlation_id(host.name, task._uuid)
        self.taskDurations[task_correlation_id] = to_millis(time.time())

        data = {
            'status': "RUNNING",
            'hostStatus': "RUNNING",
            'playbook': self.playbook._file_name,
            'host': host.name,
            'hostGroup': self.play.get_name(),
            'task': task.get_name(),
            'action': task.action,
            'correlationId': task_correlation_id,
            'phase': "pre"
        }

        self.handle_event(data)

    def _on_task_skipped(self, result):
        task_correlation_id = self._task_correlation_id(result._host.name, result._task._uuid)

        data = {
            'status': 'SKIPPED',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'action': result._task.action,
            'correlationId': task_correlation_id,
            'phase': "post",
            'duration': self._task_duration(task_correlation_id),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    ### Ansible callbacks ###

    def v2_playbook_on_start(self, playbook):
        self.playbook = playbook
        self.playbook_on_start()

    def playbook_on_stats(self, stats):
        self._record_host_statuses()
        self.outFile.close()

    def v2_runner_on_failed(self, result, ignore_errors=False):
        task_correlation_id = self._task_correlation_id(result._host.name, result._task._uuid)

        data = {
            'status': "FAILED",
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'action': result._task.action,
            'correlationId': task_correlation_id,
            'phase': "post",
            'duration': self._task_duration(task_correlation_id),
            'result': self.cleanup_results(result._result),
            'ignore_errors': ignore_errors
        }

        if not ignore_errors:
            data['hostStatus'] = "FAILED"
            self._remove_host(result._host)

        self.handle_event(data)

    def v2_runner_on_ok(self, result, **kwargs):
        self._mark_host_running(result._host)

        task_correlation_id = self._task_correlation_id(result._host.name, result._task._uuid)

        data = {
            'status': "OK",
            'hostStatus': 'RUNNING',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'action': result._task.action,
            'correlationId': task_correlation_id,
            'phase': "post",
            'duration': self._task_duration(task_correlation_id),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_unreachable(self, result):
        self._remove_host(result._host)

        task_correlation_id = self._task_correlation_id(result._host.name, result._task._uuid)

        data = {
            'status': 'UNREACHABLE',
            'hostStatus': 'UNREACHABLE',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'action': result._task.action,
            'correlationId': task_correlation_id,
            'phase': "post",
            'duration': self._task_duration(task_correlation_id),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def v2_runner_on_async_failed(self, result, **kwargs):
        self._remove_host(result._host)

        task_correlation_id = self._task_correlation_id(result._host.name, result._task._uuid)

        data = {
            'status': 'UNREACHABLE',
            'hostStatus': 'UNREACHABLE',
            'playbook': self.playbook._file_name,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'task': result._task.get_name(),
            'action': result._task.action,
            'correlationId': task_correlation_id,
            'phase': "post",
            'duration': self._task_duration(task_correlation_id),
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
