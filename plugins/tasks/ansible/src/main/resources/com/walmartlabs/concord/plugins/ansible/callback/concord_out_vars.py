import os
import json
from ansible.plugins.callback import CallbackBase
from concord_ansible_stats import ConcordAnsibleStats

try:
    from __main__ import cli
except ImportError:
    cli = None

class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_out_vars'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        super(CallbackModule, self).__init__()

        self.out_vars = None
        if "CONCORD_OUT_VARS" in os.environ:
            self.out_vars = [v.strip() for v in os.environ["CONCORD_OUT_VARS"].split(",")]

        self.out_vars_file_name = None
        if "CONCORD_OUT_VARS_FILE" in os.environ:
            self.out_vars_file_name = os.environ["CONCORD_OUT_VARS_FILE"]

        print("Saving out variables:", self.out_vars)

    def playbook_on_stats(self, stats):
        if not self.out_vars:
            return

        result = dict()

        all_vars = self.var_manager._nonpersistent_fact_cache

        for fact in self.out_vars:
            fact_by_host = dict()
            for host, vars in all_vars.items():
                if fact in vars:
                    fact_by_host[host] = vars[fact]
            result[fact] = fact_by_host

        if '_stats' in self.out_vars:
            result['_stats'] = ConcordAnsibleStats.build_stats_data(stats)

        target_file = open(self.out_vars_file_name, "w")
        target_file.write(json.dumps(result, indent=2))
        print("Variables saved to:", self.out_vars_file_name)


    def v2_playbook_on_play_start(self, play):
        self.var_manager = play.get_variable_manager()
