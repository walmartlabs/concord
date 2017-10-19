select
  cast(json_build_object('local', json_build_object('hosts', array_agg(a.ip),
                    'vars', json_build_object('ansible_connection', 'local'))) as varchar) as hosts
from (
	select b.item_data->>'ip' as ip
	from inventory_data a, inventory_data b
	where
		a.item_path like 'hosts/%/sw/app' and a.item_data @> ?::jsonb
		and a.inventory_id = b.inventory_id
		and b.item_path = substring(a.item_path, 0, position('/sw' in a.item_path)) || '/net') a