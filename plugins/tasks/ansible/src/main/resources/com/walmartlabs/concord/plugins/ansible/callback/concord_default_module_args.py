from ansible.plugins.callback import CallbackBase
from ansible.playbook.task import Task
from ansible.inventory.host import Host


class CallbackModule(CallbackBase):
    def v2_runner_on_start(self, host: Host, task: Task):
        if task.resolved_action == 'ansible.builtin.gather_facts' or task.resolved_action == 'gather_facts':
            # disable puppet / chef fact gathering, significant speed/performance increase - usually unneeded
            # may eventually need !hardware for AIX/HPUX or set at runtime, Ansible 2.4 fixes many broken facts
            module_defaults = self._get_defaults(task)

            if ((task.resolved_action not in module_defaults) or
                    module_defaults[task.resolved_action]['gather_subset'] is None):

                # no module_defaults are set for this module
                # now make sure there are no task args
                if 'gather_subset' not in task.args or task.args['gather_subset'] is None:
                    task.args['gather_subset'] = ['!facter', '!ohai']

    def _get_defaults(self, task: Task):
        for mod_def in task.module_defaults:
            if task.resolved_action in mod_def and mod_def[task.resolved_action] is not None:
                return mod_def

        return dict()
