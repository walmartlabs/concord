from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

try:
    from __main__ import display
except ImportError:
    from ansible.utils.display import Display
    display = Display()

import json
import re
import os
import os.path

class TaskPolicy:

    def __init__(self):
        rule_file = os.environ['CONCORD_POLICY']

        self.policy_rules = dict()

        if os.path.isfile(rule_file):
            print("Loading policy from {}".format(rule_file))
            self.policy_rules = json.load(open(rule_file))

    def is_deny(self, task):
        if 'ansible' not in self.policy_rules:
            return False

        ansible_rules = self.policy_rules['ansible']

        action = task.action
        args = task._attributes.get('args')

        if 'allow' in ansible_rules:
            for r in ansible_rules['allow']:
                if self._match_ansible_rule(r, action, args):
                    return False

        if 'deny' in ansible_rules:
            for r in ansible_rules['deny']:
                if self._match_ansible_rule(r, action, args):
                    display.error("Task '{}' is forbidden by the task policy {}".format(task.get_name(), r))
                    return True

        if 'warn' in ansible_rules:
            for r in ansible_rules['warn']:
                if self._match_ansible_rule(r, action, args):
                    display.warning("Potentially restricted task '{}' (task policy: {})".format(task.get_name(), r))
                    return False

        return False

    def _match_ansible_rule(self, rule, task_action, task_args):
        display.vv("match_ansible_rule: {} on {}".format(rule, task_action, task_args))

        if not self._match(rule['action'], task_action):
            return False

        if 'params' in rule and not self._match_task_args(rule['params'], task_args):
            return False

        return True

    def _match_task_args(self, rule_args, task_args):
        display.vv("match_task_args: {} on {}".format(rule_args, task_args))

        matched = False
        for a in rule_args:
            for ta_name, ta_value in task_args.iteritems():
                if self._match(a['name'], ta_name):
                    if self._match_values(a['values'], ta_value):
                        matched = True
                    else:
                        return False
                    break

        return matched

    def _match_values(self, patterns, value):
        display.vv("match_values: {} on {}".format(patterns, value))

        if isinstance(value, basestring):
            for p in patterns:
                if self._match(p, value):
                    return True

        return False

    @staticmethod
    def _match(pattern, s):
        return re.compile(pattern).match(s)
