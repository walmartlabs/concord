from ansible.plugins.callback import CallbackBase
import collections

class CallbackModule(CallbackBase):

    '''
    This is the callback interface, which will search for any sensitive data in stdout and enable no_log = true on that particular tasks.
    Also this will search for all Key, Value pairs in the ansible stdout and mask the value of corresponding keys that has got the sensitive information in it.
    '''

    CALLBACK_VERSION = 2.0
    CALLBACK_TYPE = 'stdout'
    CALLBACK_NAME = 'concord_protectdata'
    CALLBACK_NEEDS_WHITELIST = False

    def __init__(self):
        self.secret_list = ['password', 'pwd', 'credentials', 'secret', 'ansible_password', 'vaultpassword']
        print("Log filter is enabled...")
    
    def hide_password(self, result):
        ret = {}
        for key, value in result.iteritems():
            if isinstance(value, collections.Mapping):
                ret[key] = self.hide_password(value)
            elif any (x in str(value).lower() for x in self.secret_list) or any (y in str(key).lower() for y in self.secret_list):
                    ret[key] = "******" 
            else:       
                    ret[key] = value
        return ret

    def v2_playbook_on_task_start(self, task, is_conditional):
        print("TASK", "[",task.get_name(),"]" , "********************************************************************")
        task_args = task._attributes.get('args')
        for k, v in task_args.iteritems():
           if any(s in str(v).lower() for s in self.secret_list):
             print("***********THIS TASK CONTAINS SENSITIVE INFORMATION. ENABLING NO_LOG******************")
             task.no_log = True     
    
    def _dump_results(self, result,  indent=None, sort_keys=True, keep_invocation=False):
         return super(CallbackModule, self)._dump_results(self.hide_password(result), indent, sort_keys, keep_invocation)
