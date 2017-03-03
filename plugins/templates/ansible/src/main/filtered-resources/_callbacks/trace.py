import os
import json
from ansible.plugins.callback import CallbackBase

try:
    from __main__ import cli
except ImportError:
    cli = None


class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_TYPE = 'notification'
    CALLBACK_NAME = 'concord_trace'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        super(CallbackModule, self).__init__()
        self.base_dir = os.environ['_CONCORD_ATTACHMENTS_DIR']

    def log(self, data):
        target_dir = self.base_dir;
        os.makedirs(target_dir)

        target_filename = target_dir + "/ansible_stats.json";
        target_file = open(target_filename, "w")
        target_file.write(json.dumps(data, indent=2))

        print "Trace saved to: ", target_filename

    def playbook_on_stats(self, stats):
        data = {
            'ok': stats.ok.keys(),
            'failures': stats.failures.keys(),
            'unreachable': stats.dark.keys(),
            'changed': stats.changed.keys(),
            'skipped': stats.skipped.keys()
        }
        self.log(data)
