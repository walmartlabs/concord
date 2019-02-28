from __future__ import (absolute_import)
__metaclass__ = type

from ansible.template import Templar
from ansible.plugins.callback import CallbackBase
from ansible.executor.task_executor import TaskExecutor
from ansible.errors import AnsibleError

try:
    from __main__ import display
except ImportError:
    from ansible.utils.display import Display
    display = Display()


from task_policy import TaskPolicy

import copy

taskPolicy = TaskPolicy()

### copied from ansible/executor/task_executor.py with added concord policy check logic
def _execute(self, variables=None):
    if variables is None:
        variables = self._job_vars

    templar = Templar(loader=self._loader, shared_loader_obj=self._shared_loader_obj, variables=variables)

    _task = copy.deepcopy(self._task)

    context_validation_error = None
    try:
        # apply the given task's information to the connection info,
        # which may override some fields already set by the play or
        # the options specified on the command line
        self._play_context = self._play_context.set_task_and_variable_override(task=_task, variables=variables, templar=templar)

        # fields set from the play/task may be based on variables, so we have to
        # do the same kind of post validation step on it here before we use it.
        self._play_context.post_validate(templar=templar)

        # now that the play context is finalized, if the remote_addr is not set
        # default to using the host's address field as the remote address
        if not self._play_context.remote_addr:
            self._play_context.remote_addr = self._host.address

        # We also add "magic" variables back into the variables dict to make sure
        # a certain subset of variables exist.
        self._play_context.update_vars(variables)

        # FIXME: update connection/shell plugin options
    except AnsibleError as e:
        # save the error, which we'll raise later if we don't end up
        # skipping this task during the conditional evaluation step
        context_validation_error = e

    # Evaluate the conditional (if any) for this task, which we do before running
    # the final task post-validation. We do this before the post validation due to
    # the fact that the conditional may specify that the task be skipped due to a
    # variable not being present which would otherwise cause validation to fail
    try:
        if not _task.evaluate_conditional(templar, variables):
            display.debug("when evaluation is False, skipping this task")
            return dict(changed=False, skipped=True, skip_reason='Conditional result was False', _ansible_no_log=self._play_context.no_log)
    except AnsibleError:
        # loop error takes precedence
        if self._loop_eval_error is not None:
            raise self._loop_eval_error  # pylint: disable=raising-bad-type
        # skip conditional exception in the case of includes as the vars needed might not be available except in the included tasks or due to tags
        if _task.action not in ['include', 'include_tasks', 'include_role']:
            raise

    # Not skipping, if we had loop error raised earlier we need to raise it now to halt the execution of this task
    if self._loop_eval_error is not None:
        raise self._loop_eval_error  # pylint: disable=raising-bad-type

    # if we ran into an error while setting up the PlayContext, raise it now
    if context_validation_error is not None:
        raise context_validation_error  # pylint: disable=raising-bad-type

    # if this task is a TaskInclude, we just return now with a success code so the
    # main thread can expand the task list for the given host
    if _task.action in ('include', 'include_tasks'):
        include_variables = _task.args.copy()
        include_file = include_variables.pop('_raw_params', None)
        if not include_file:
            return dict(failed=True, msg="No include file was specified to the include")

        include_file = templar.template(include_file)
        return dict(include=include_file, include_variables=include_variables)

    # if this task is a IncludeRole, we just return now with a success code so the main thread can expand the task list for the given host
    elif _task.action == 'include_role':
        include_variables = _task.args.copy()
        return dict(include_variables=include_variables)

    # Now we do final validation on the task, which sets all fields to their final values.
    _task.post_validate(templar=templar)
    if '_variable_params' in _task.args:
        variable_params = _task.args.pop('_variable_params')
        if isinstance(variable_params, dict):
            display.deprecated("Using variables for task params is unsafe, especially if the variables come from an external source like facts",
                               version="2.6")
            variable_params.update(_task.args)
            _task.args = variable_params

    if taskPolicy.is_deny(_task):
        self._task = copy.deepcopy(self._task)
        self._task.action = "fail"
        self._task.args = {"msg": "Found forbidden tasks"}

    return ansibleTaskExecutorExecute(self, variables)

class CallbackModule(CallbackBase):

    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_task_executor_patch'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        global ansibleTaskExecutorExecute
        ansibleTaskExecutorExecute = TaskExecutor._execute
        TaskExecutor._execute = _execute

        super(CallbackModule, self).__init__()