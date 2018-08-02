from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

class ConcordAnsibleStats:

    @staticmethod
    def build_stats_data(stats):
        failures = stats.failures.keys()

        unreachable = [e for e in stats.dark.keys()
                       if e not in failures]

        changed = [e for e in stats.changed.keys()
                   if (e not in failures
                       and e not in unreachable)]

        ok = [e for e in stats.ok.keys()
              if (e not in failures
                  and e not in unreachable
                  and e not in changed)]

        skipped = [e for e in stats.skipped.keys()
                   if (e not in failures
                       and e not in unreachable
                       and e not in changed
                       and e not in ok)]

        return {
            'ok': ok,
            'failures': failures,
            'unreachable': unreachable,
            'changed': changed,
            'skipped': skipped
        }
