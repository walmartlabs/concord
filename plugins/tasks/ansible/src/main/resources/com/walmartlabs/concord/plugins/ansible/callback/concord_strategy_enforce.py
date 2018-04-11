from __future__ import (absolute_import)
__metaclass__ = type

from ansible.plugins.callback import CallbackBase

import sys

class CallbackModule(CallbackBase):

    CALLBACK_VERSION = 2.0
    CALLBACK_NAME = 'concord_strategy_enforce'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        super(CallbackModule, self).__init__()

    def v2_playbook_on_play_start(self, play):
        if play.strategy == "linear":
            play.strategy = "concord_linear"
        elif play.strategy == "free":
            play.strategy = "concord_free"
        else:
            print "Invalid strategy: {}".format(play.strategy)
            sys.exit(-1)

        print "play strategy '{}'".format(play.strategy)