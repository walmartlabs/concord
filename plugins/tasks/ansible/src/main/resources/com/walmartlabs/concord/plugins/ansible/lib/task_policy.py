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
import string
import ansible

class SafeDict(dict):
    def __missing__(self, key):
        return '{' + key + '}'

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

        if not hasattr(task, 'args'):
            return False

        task_args = getattr(task, 'args', None)
        
        args = self._enrich_args(action, task_args)

        if 'allow' in ansible_rules:
            for r in ansible_rules['allow']:
                if self._match_ansible_rule(r, action, args):
                    return False

        if 'deny' in ansible_rules:
            for r in ansible_rules['deny']:
                if self._match_ansible_rule(r, action, args):
                    display.error("Task '{0} ({1})' is forbidden by the task policy: {2}".format(
                        task.get_name(), action, self._format_rule_message(r, args)))
                    return True

        if 'warn' in ansible_rules:
            for r in ansible_rules['warn']:
                if self._match_ansible_rule(r, action, args):
                    display.warning("Potentially restricted task '{0} ({1})' (task policy: {2})".format(
                        task.get_name(), action, self._format_rule_message(r, args)))
                    return False

        return False

    @staticmethod
    def _format_rule_message(rule, args):
        if 'msg' not in rule:
            return ''

        args_dict = rule.copy()
        for ta_name, ta_value in args.items():
            args_dict[ta_name] = ta_value

        return string.Formatter().vformat(rule['msg'], (), SafeDict(args_dict))

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
            for ta_name, ta_value in task_args.items():
                if self._match(a['name'], ta_name):
                    if self._match_values(a['values'], ta_value):
                        matched = True
                    else:
                        return False
                    break

        return matched

    def _match_values(self, patterns, value):
        display.vv("match_values: {} on {}".format(patterns, value))

        if isinstance(value, ansible.parsing.yaml.objects.AnsibleUnicode):
            value = str(value)

        if isinstance(value, str):
            for p in patterns:
                if self._match(p, value):
                    return True

        return False

    def _enrich_args(self, action, args):
        display.vv("enrich_args: {} ({})".format(action, args))

        new_args = args
        if action == 'maven_artifact':
            new_args = args.copy()
            new_args['artifact_url'] = self._build_artifact_url(args)

        return new_args

    # /$groupId[0]/../$groupId[n]/$artifactId/$version/$artifactId-$version-$classifier.$extension
    def _build_artifact_url(self, args):
        name = args['artifact_id'] + '-' + args['version']
        if 'classifier' in args:
            name = name + '-' + args['classifier']
        name = name + '.' + args.get('extension', 'jar')

        return args['repository_url'].rstrip('/') + '/' + args['group_id'].replace('.', '/') + '/' + args['artifact_id'] + '/' + args['version'] + '/' + name

    @staticmethod
    def _match(pattern, s):
        return re.compile(pattern).match(s)
