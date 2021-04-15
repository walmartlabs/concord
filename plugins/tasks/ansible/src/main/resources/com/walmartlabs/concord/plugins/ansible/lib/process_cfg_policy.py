from __future__ import (absolute_import, division, print_function)

from ansible.utils.color import hostcolor
__metaclass__ = type

try:
    from __main__ import display
except ImportError:
    from ansible.utils.display import Display
    display = Display()

import json
import os
import os.path

class ProcessCfgPolicy:

    def __init__(self):
        rule_file = os.environ['CONCORD_POLICY']

        # default value
        verbose_limits = {'maxHosts': None, 'maxTotalWork': None}

        if os.path.isfile(rule_file):
            print("Loading policy from {}".format(rule_file))
            policy_rules = json.load(open(rule_file))

            try:
                verbose_limits = policy_rules['processCfg']['arguments']['ansibleVerboseLimits']
            except KeyError:
                pass

        self.max_hosts = verbose_limits.get('maxHosts')
        self.max_total_work = verbose_limits.get('maxTotalWork')

    def disable_verbose_after_too_much_work(self, completed_work):
        '''
        Methods for disabling verbose output after playbook starts execution. Useful
        when the main playbook has few tasks but includes one or more include_tasks
        calls which bypasses total_work calculations before playbook starts.
        '''

        if display.verbosity == 0:
            return False

        if self.max_total_work is not None and completed_work > self.max_total_work:
            msg = """
Disabling verbose output. Too much work for verbose logging. Completed {0} work
so far and Concord system policies forbid verbose logging for playbooks with
greater than {1} total work.
            """.format(str(completed_work), self.max_total_work)
            display.error(msg)
            display.verbosity = 0

    def disable_verbose_on_start(self, host_count, total_work):
        '''
        Disables verbose logging if policy exists to limit total number of inventory
        hosts or total work.
        '''

        if display.verbosity == 0:
            return

        if self.max_hosts is not None and host_count > self.max_hosts:
            msg = """
Disabling verbose output. Too many hosts for verbose logging. Inventory contains
{0} hosts and Concord system policies forbid verbose logging for inventories
greater than {1} hosts.
            """.format(host_count, str(self.max_hosts))
            display.error(msg)
            display.verbosity = 0
            return

        if self.max_total_work is not None and total_work > self.max_total_work:
            msg = """
Disabling verbose output. Too much work for verbose logging. Expecting to perform
{0} total work and Concord system policies forbid verbose logging for playbooks
with greater than {1} total work.
            """.format(str(total_work), self.max_total_work)
            display.error(msg)
            display.verbosity = 0
