from __future__ import (absolute_import)
__metaclass__ = type

from ansible.plugins.callback.default import CallbackModule as CallbackModule_default
import collections

def enc(s):
    # we shouldn't use `str` as the input string can contain non-ascii characters
    # and we can't use `s.encode('utf-8')` because the input can be a non string value
    return repr(s)

class CallbackModule(CallbackModule_default):

    '''
    This is the callback interface, which will search for any sensitive data in stdout and enable no_log = true on that particular tasks.
    Also this will search for all Key, Value pairs in the Ansible stdout and mask the value of corresponding keys that has got the sensitive information in it.
    '''

    CALLBACK_VERSION = 2.0
    CALLBACK_TYPE = 'stdout'
    CALLBACK_NAME = 'concord_protectdata'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        super(CallbackModule, self).__init__()
        self.secret_list = ['password', 'credentials', 'secret', 'ansible_password', 'vaultpassword']
        print "Log filter is enabled..."
    
    def hide_password(self, result):
        ret = {}
        for key, value in result.iteritems():
            if isinstance(value, collections.Mapping):
                ret[key] = self.hide_password(value)
            elif any (x in enc(value).lower() for x in self.secret_list) or any (y in enc(key).lower() for y in self.secret_list):
                    ret[key] = "******"
            else:       
                    ret[key] = value
        return ret

    def v2_playbook_on_task_start(self, task, is_conditional):
        print "TASK", "[",task.get_name(),"]" , "*************************************************************************"
        task_args = task._attributes.get('args')
        for k, v in task_args.iteritems():
           if any(s in enc(v).lower() for s in self.secret_list):
             print "*********** THIS TASK CONTAINS SENSITIVE INFORMATION. ENABLING NO_LOG ******************"
             task.no_log = True
    
    def _dump_results(self, result,  indent=None, sort_keys=True, keep_invocation=False):
         return super(CallbackModule, self)._dump_results(self.hide_password(result), indent, sort_keys, keep_invocation)
