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

        self.policy_rules = dict()

        if os.path.isfile(rule_file):
            print("Loading policy from {}".format(rule_file))
            self.policy_rules = json.load(open(rule_file))

            try:
                self.verbose_limits = self.policy_rules['processCfg']['arguments']['ansibleVerboseLimits']
            except KeyError:
                self.verbose_limits = {'maxHosts': None, 'maxTotalWork': None}


    def is_deny_verbose_logging(self, host_count, total_work):
        if display.verbosity == 0:
            return False

        max_hosts = self.verbose_limits.get('maxHosts')
        max_total_work = self.verbose_limits.get('maxTotalWork')

        if max_hosts is not None and host_count > max_hosts:
            msg = """
Too many hosts for verbose logging. Inventory contains {0} hosts and Concord
system policies forbid verbose logging for inventories greater than {1} hosts.
            """.format(host_count, str(max_hosts))
            display.error(msg)
            return True

        if max_total_work is not None and total_work > max_total_work:
            msg = """
Too much work for verbose logging. Expecting to perform {0} total work and
Concord system policies forbid verbose logging for playbooks with greater than {1} total work.
            """.format(str(total_work), max_total_work)
            display.error(msg)
            return True

        return False

    def get_policy_value(self, key):
        try:
            self.policy_rules['processCfg']['arguments'][key]
        except KeyError:
            # no policy set
            return -1
