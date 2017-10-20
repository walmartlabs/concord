select cast(a.item_data->'ansible_default_ipv4'->'address' as varchar) as HOST_IP
from inventory_data a
where
  item_path like '%/ansible_facts' and
  item_data @> ?::jsonb