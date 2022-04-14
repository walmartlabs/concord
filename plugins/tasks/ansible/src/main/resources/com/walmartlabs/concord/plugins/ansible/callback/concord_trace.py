import errno
import os
import json
from ansible.plugins.callback import CallbackBase
from concord_ansible_stats import ConcordAnsibleStats

try:
    from __main__ import cli
except ImportError:
    cli = None


def mkdir_p(path):
    try:
        os.makedirs(path)
    except OSError as exc:
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_trace'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        super(CallbackModule, self).__init__()
        self.base_dir = os.environ['_CONCORD_ATTACHMENTS_DIR']
        self.eventCorrelationId = None
        if "CONCORD_EVENT_CORRELATION_ID" in os.environ:
            self.eventCorrelationId = os.environ['CONCORD_EVENT_CORRELATION_ID']

    def log(self, data):
        target_dir = self.base_dir
        mkdir_p(target_dir)

        target_filename = target_dir + "/ansible_stats.json"
        target_file = open(target_filename, "w")
        target_file.write(json.dumps(data, indent=2))

        print("Trace saved to:", target_filename)

        entry = {'playbook': self.playbook._file_name,
                 'eventCorrelationId': self.eventCorrelationId,
                 'stats': data}

        target_filename = target_dir + "/ansible_stats_v2.json"
        if not os.path.isfile(target_filename):
            with open(target_filename, mode='w') as f:
                f.write(json.dumps([entry], indent=2))
        else:
            with open(target_filename) as f:
                stats = json.load(f)

            stats.append(entry)
            with open(target_filename, mode='w') as f:
                f.write(json.dumps(stats, indent=2))

        print("Trace saved to:", target_filename)

    def v2_playbook_on_start(self, playbook):
        self.playbook = playbook

    def playbook_on_stats(self, stats):
        self.log(ConcordAnsibleStats.build_stats_data(stats))
