import sys
sys.path.append(".") # add local directory as a module path

import my_module

# 'result' collects the script outputs; 'x' is a Concord process variable.
my_module.test(result, x)

result.set('y2', my_module.test2(x))