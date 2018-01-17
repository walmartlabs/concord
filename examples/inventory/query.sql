select cast(
    json_build_object(
        'host', a.item_data->'ansible_default_ipv4'->'address',
        'ansible_connection', 'local'
    ) as varchar)
from inventory_data a
where
  item_path like '%/ansible_facts' and
  item_data @> ?::jsonb