from __future__ import (absolute_import)
__metaclass__ = type

from ansible.plugins.callback import CallbackBase
from ansible.plugins.strategy import StrategyBase

def _queue_task(self, host, task, task_vars, play_context):
    self._tqm.send_callback('concord_on_task_start', host, task)

    ansibleStrategyModuleQueueTask(self, host, task, task_vars, play_context)


class CallbackModule(CallbackBase):

    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_strategy_patch'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        global ansibleStrategyModuleQueueTask
        ansibleStrategyModuleQueueTask = StrategyBase._queue_task
        StrategyBase._queue_task = _queue_task

        super(CallbackModule, self).__init__()