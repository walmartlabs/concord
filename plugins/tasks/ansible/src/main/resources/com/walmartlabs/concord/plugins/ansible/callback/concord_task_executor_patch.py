from __future__ import (absolute_import)
__metaclass__ = type

from ansible.errors import AnsibleError
from ansible.executor.task_executor import TaskExecutor
from ansible.plugins.callback import CallbackBase

from task_policy import TaskPolicy

taskPolicy = TaskPolicy()
_original_get_action_handler_with_module_context = TaskExecutor._get_action_handler_with_module_context


def _get_action_handler_with_module_context(self, templar):
    if taskPolicy.is_deny(self._task):
        raise AnsibleError("Found forbidden tasks")

    return _original_get_action_handler_with_module_context(self, templar)


class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_task_executor_patch'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        TaskExecutor._get_action_handler_with_module_context = _get_action_handler_with_module_context
        super(CallbackModule, self).__init__()