import ujson as json
import os
import errno
from ansible.plugins.callback import CallbackBase

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
    CALLBACK_TYPE = 'notification'
    CALLBACK_NAME = 'concord_trace'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        super(CallbackModule, self).__init__()
        self.base_dir = os.environ['_CONCORD_ATTACHMENTS_DIR']

    def log(self, data):
        target_dir = self.base_dir;
        mkdir_p(target_dir)

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
