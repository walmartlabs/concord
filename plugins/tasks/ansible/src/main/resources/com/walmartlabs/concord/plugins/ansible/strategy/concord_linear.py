from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

from ansible.plugins.strategy.linear import StrategyModule as StrategyModule_linear

from task_policy import TaskPolicy

import copy

class StrategyModule(StrategyModule_linear):

    def __init__(self, tqm):
        self._tqm = tqm
        self._task_policy = TaskPolicy()
        super(StrategyModule, self).__init__(tqm)

    def _queue_task(self, host, task, task_vars, play_context):
        if self._task_policy.is_deny(task):

            _task = copy.deepcopy(task)
            _task.action = "fail"
            _task.args = {"msg": "Found forbidden tasks"}

            super(StrategyModule, self)._queue_task(host, _task, task_vars, play_context)
            return

        self._tqm.send_callback('concord_on_task_start', host, task)

        super(StrategyModule, self)._queue_task(host, task, task_vars, play_context)

