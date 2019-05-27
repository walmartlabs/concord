from __future__ import (absolute_import, division, print_function)

__metaclass__ = type

import os
import sys

sys.path.insert(1, os.path.join(os.path.dirname(__file__), os.pardir, "_lookups"))
from concord_data_secret import LookupModule as LookupSecret


# This lookup plugin will be removed in future - Kindly use 'concord_data_secret'
class LookupModule(LookupSecret):
    pass
