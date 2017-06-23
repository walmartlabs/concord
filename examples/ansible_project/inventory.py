#!/usr/bin/python
import argparse

try:
    import json
except ImportError:
    import simplejson as json

class ExampleInventory(object):
    def __init__(self):
        print json.dumps(self.example_inventory());
    def example_inventory(self):
        return {
          "local": {
 	    "hosts": ["127.0.0.1"],
            "vars": {
              "ansible_connection": "local"
            }
          }
        }

# Get the inventory.
ExampleInventory()
