from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

from ansible.plugins.callback import CallbackBase
from ansible.module_utils.six import string_types
from ansible.playbook.block import Block
from ansible.playbook.handler import Handler

import time
import datetime

import os
import ujson as json
from sets import Set

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

    def _handle_event(self, type, event):
        self.outFile.write(json.dumps({
            'eventType': type,
            'eventDate': datetime.datetime.utcnow().isoformat() + 'Z',
            'data': dict(event, **{'parentCorrelationId': self.eventCorrelationId,
                                   'currentRetryCount': self.currentRetryCount})
        }))
        self.outFile.write('<~EOL~>\n')
        self.outFile.flush()

    def handle_event(self, event):
        self._handle_event('ANSIBLE', event)

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
        self.hostStatus.pop(correlation_id, None)

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
            'playId': self.play._uuid,
            'host': host.name,
            'hostGroup': self.play.get_name(),
            'taskId': task._uuid,
            'task': task.get_name(),
            'isHandler': isinstance(task, Handler),
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
            'playId': self.play._uuid,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'taskId': result._task._uuid,
            'task': result._task.get_name(),
            'isHandler': isinstance(result._task, Handler),
            'action': result._task.action,
            'correlationId': task_correlation_id,
            'phase': "post",
            'duration': self._task_duration(task_correlation_id),
            'result': self.cleanup_results(result._result)
        }

        self.handle_event(data)

    def handle_playbook_start(self, playbook):
        def _process_task(task):
            result = []
            if isinstance(task, Block):
                result.extend(_process_block(task))
            else:
                if task.action == 'meta':
                    return []

                result.append({
                    'id': task._uuid,
                    'task': task.get_name()
                })

            return result

        def _process_block(b):
            result = []
            for task in b.block:
                result.extend(_process_task(task))

            for task in b.rescue:
                result.extend(_process_task(task))

            for task in b.always:
                result.extend(_process_task(task))

            return result

        def _collect_tasks(play):
            result = []
            for block in play.compile():
                if not block.has_tasks():
                    continue
                tasks = _process_block(block)

                for x in tasks:
                    if x not in result:
                        result.append(x)

            return result

        total_work = 0
        hosts = Set()
        info = []
        for p in playbook.get_plays():
            play_hosts = p.get_variable_manager()._inventory.get_hosts(p.hosts)
            play_hosts_count = len(play_hosts)
            play_task = _collect_tasks(p)

            play_info = {
                'id': p._uuid,
                'play': p.get_name(),
                'hosts': play_hosts_count,
                'tasks': play_task
            }
            info.append(play_info)
            hosts.update(play_hosts)
            total_work += play_hosts_count * len(play_task)

        self._handle_event('ANSIBLE_PLAYBOOK_INFO', {'playbook':playbook._file_name,
                                                     'uniqueHosts': len(hosts),
                                                     'totalWork': total_work,
                                                     'plays': info})

    @staticmethod
    def _get_playbook_status(stats):
        if len(stats.failures) > 0 or len(stats.dark) > 0:
            return 'FAILED'
        return 'OK'

    ### Ansible callbacks ###

    def v2_playbook_on_start(self, playbook):
        self.playbook = playbook
        self.handle_playbook_start(playbook)
        self.playbook_on_start()

    def playbook_on_stats(self, stats):
        self._record_host_statuses()

        status = self._get_playbook_status(stats)
        self._handle_event('ANSIBLE_PLAYBOOK_RESULT', {'playbook': self.playbook._file_name,
                                                       'status': status})

        self.outFile.close()

    def v2_runner_on_failed(self, result, ignore_errors=False):
        task_correlation_id = self._task_correlation_id(result._host.name, result._task._uuid)

        data = {
            'status': "FAILED",
            'playbook': self.playbook._file_name,
            'playId': self.play._uuid,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'taskId': result._task._uuid,
            'task': result._task.get_name(),
            'isHandler': isinstance(result._task, Handler),
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
            'playId': self.play._uuid,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'taskId': result._task._uuid,
            'task': result._task.get_name(),
            'isHandler': isinstance(result._task, Handler),
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
            'playId': self.play._uuid,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'taskId': result._task._uuid,
            'task': result._task.get_name(),
            'isHandler': isinstance(result._task, Handler),
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
            'playId': self.play._uuid,
            'host': result._host.name,
            'hostGroup': self.play.get_name(),
            'taskId': result._task._uuid,
            'task': result._task.get_name(),
            'isHandler': isinstance(result._task, Handler),
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

    def v2_playbook_on_play_start(self, play):
        self.play = play
