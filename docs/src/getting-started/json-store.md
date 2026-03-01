# JSON Store

JSON Store provides a built-in mechanism of storing and querying for
arbitrary JSON data persistently. It is useful for processes which require
state management beyond regular variables or features provided by
[the Key Value store]({{ site.concord_plugins_v2_docs }}/key-value.md).

**Note:** JSON Store supersedes the old Inventory and Inventory Query APIs.
Existing users are encouraged to switch to the JSON Store API. The data created
using the old API is available both trough the Inventory and JSON Store APIs.

## Concepts

Any Concord [organization](./orgs.md) can contain multiple JSON stores.
Each store must have a name that's unique for that organization. Just like projects
or secrets, JSON stores can be either _public_ or _private_. Data in public
stores can be read by any user in the same organization as the store.
Private stores require explicit access rules.

The total size of a store and the maximum allowed number of stores can be
restricted using [policies](./policies.md#json-store-rule). 

Each store can contain multiple _items_. Each item is a well-formed JSON
document -- Concord performs syntax validation whenever a document is added or
updated. Documents are identified by their "path" in the store, each path must
be unique and can contain only one document.

Items can be added or retrieved using [the API](../api/json-store.md),
by using [the JSON Store task]({{ site.concord_plugins_v2_docs }}/json-store.md) or using
[named queries](#named-queries). 

## Named Queries

Named queries can be used to retrieve multiple items at once, perform
aggregations and filtering on the fly.

Queries use SQL:2011 syntax with [PostgreSQL 10 extensions for JSON](https://www.postgresql.org/docs/10/functions-json.html).
When executing a query, Concord automatically limits it to the query's store by
adding the store ID condition. All queries are read only and can only access
the `JSON_STORE_DATA` table.

Query parameters can be passed as JSON objects when the query is executed. Note
that only valid JSON objects are allowed. If you wish to pass an array or a
literal value as a query parameter then you need to wrap it into an object (see
[the example below](#example)).

Queries can be created and executed by using [the API](../api/json-store.md),
by using [the task]({{ site.concord_plugins_v2_docs }}/json-store.md#execute-a-named-query) or in the
Concord Console, which provides a way to execute and preview results of a query
before saving it.

The result of execution is a JSON array of rows returned by the query. All
values must be representable in JSON - strings, numbers, booleans, arrays and
objects. Currently, there are no limitations on how many rows or columns a query
can return (subject to change).

## Limitations

The following PostgreSQL JSON(b) operators are not supported: `?`, `?|` and `?&`.

Query arguments are not supported when executing queries in the Concord Console.

## Example

Let's create a simple user database of some fictional services. All operations
except uploading the data can be performed in the Concord Console, but we're
going to use `curl` for this example.

The example uses the `Default` Concord organization. Depending on your Concord
instance's configuration it might not be available. In this case, replace
`Default` with the name of your organization.

First, create a store:

```
$ curl -ikn -X POST \
-H 'Content-Type: application/json' \
-d '{"name": "myStore"}' \
https://concord.example.com/api/v1/org/Default/jsonstore

{
  "result" : "CREATED",
  "ok" : true
}
```

Then we can add some data into the new store:

```
$ curl -ikn -X PUT \
-H 'Content-Type: application/json' \
-d '{"service": "service_a", "users": ["bob", "alice"]}' \
https://concord.example.com/api/v1/org/Default/jsonstore/myStore/item/service_a

$ curl -ikn -X PUT \
-H 'Content-Type: application/json' \
-d '{"service": "service_b", "users": ["alice", "mike"]}' \
https://concord.example.com/api/v1/org/Default/jsonstore/myStore/item/service_b
```

Check if the data is there:

```
$ curl -ikn https://concord.example.com/api/v1/org/Default/jsonstore/myStore/item/service_a

{"users": ["bob", "alice"], "service": "service_a"}
```

Now let's create a simple named query that we can use to find a `service` value
by user.

First, create a JSON file with the query definition:

```json
{
  "name": "lookupServiceByUser",
  "text": "select item_data->'service' from json_store_data where item_data @> ?::jsonb"
}
```

Next, register the query:

```
$ curl -ikn -X POST \
-H 'Content-Type: application/json' \
-d @/tmp/query.json \
https://concord.example.com/api/v1/org/Default/jsonstore/myStore/query
```

(replace `/tmp/query.json` with the path of the created file).

Execute the query:

```
curl -ikn -X POST \
-H 'Content-Type: application/json' \
-d '{ "users": ["mike"] }' \
https://concord.example.com/api/v1/org/Default/jsonstore/myStore/query/lookupServiceByUser/exec

[ "service_b" ]
```

Let's take a closer look at the query:

```sql
select item_data->'service' from json_store_data where item_data @> ?::jsonb
```

We passed `{ "users": ["mike"] }` as the query parameter. If there's a document
with a `users` property that contains a string value `mike` then the `service`
value of the same document is returned. In this case, the query returns
`[ "service_b" ]`.
