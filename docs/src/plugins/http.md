# HTTP

The HTTP task provides a basic HTTP/RESTful client that allows you to call
RESTful endpoints. It is provided automatically by Concord, and does not
require any external dependencies.

RESTful endpoints are very commonly used and often expose an API to work with
an application. The HTTP task allows you to invoke any exposed functionality in
third party applications in your Concord project and therefore automate the
interaction with these applications. This makes the HTTP task a very powerful
tool to integrate Concord with applications that do not have a custom
integration with Concord via a specific task.

The HTTP task executes RESTful requests using a HTTP `GET`, `PUT`, `PATCH`,
`POST`, or `DELETE` method and returns [HTTP response](#http-task-response)
objects.

> The HTTP Task automatically follows redirect URLs for all methods if
> the response returns status code 301. To disable this feature, set
> the **`followRedirects`** task parameter to **`false`**.

- [Usage and Configuration](#usage)
- [Examples](#examples)

<a name="usage"/>

## Usage and Configuration

As with all tasks you can invoke the HTTP task with a short inline syntax or 
the full `task` syntax.

The simple inline syntax uses an expression with the http task and the 
`asString` method. It uses a HTTP `GET` as a default request method and returns
the response as string.

```yaml
- log: "${http.asString('https://api.example.com:port/path/test.txt')}"
```

The full syntax is preferred since it allows access to all features of the HTTP
task:

```yaml
- task: http
  in:
    method: GET
    url: "https://api.example.com:port/path/endpoint"
    response: string
  out: response
- if: ${response.ok}
  then:
   - log: "Response received: ${response.content}"
```

All parameters sorted in alphabetical order.

- `auth`: authentication used for secure endpoints, details in
  the [Authentication](#authentication) section;
- `body`: the request body, details in [Body](#body);
- `connectTimeout`: HTTP connection timeout in ms. Default value is 30000 ms;
- `debug`: boolean, output the request and response data in the logs;
- `followRedirects`: boolean, determines whether redirects should be handled automatically. 
Default is `true`. Allows automatic redirection for all `methods` if not
explicitly set to `false`;
- `headers`: add additional headers, details in [Headers](#headers);
- `ignoreErrors`: boolean, instead of throwing exceptions on unauthorized requests,
  return the result object with the error;
- `keystorePath`: string, optional, path of a keystore file(.p12 or .pfx), 
  used for client-cert authentication;
- `keystorePassword`: string, keystore password;
- `method`: HTTP request method, either `POST`, `PUT`, `PATCH`, `GET`, or 
`DELETE`. Default value is `GET`;
- `proxy`: HTTP(s) proxy to use (see the [example](#proxy-usage));
- `proxyAuth`: proxy authentication details in the [Proxy Authentication](#proxy-authentication) section;
- `query`: request query parameters, details in [Query Parameter](#query-parameters);
- `request`: type of request data `string`, `json`, or `file`, details available
   in [Request type](#request-type);
- `requestTimeout`: request timeout in ms, which is the maximum time spent 
waiting for the response;
- `response`: type of response data `string`, `json`, or `file` received from
  the endpoint, details in [Response type](#response-type);
- `socketTimeout`: socket timeout in ms, which is the maximum time of inactivity
between two data packets. Default value is `-1`, which means that the default
value of the Java Runtime Environment running the process is used - common value
is 60000 ms;
- `strictSsl`: boolean, set `true` to enable ssl connection. Default value is `false`;
- `truststorePath`: string, optional, path of a truststore file(.jks), overrides 
   system's default certificate truststore;
- `truststorePassword`: string, truststore password;
- `url`: complete URL in string for HTTP request;

### Authentication

The `auth` parameter is optional. When used, it must contain the `basic` nested
element which contains either the `token` element, or the `username` and
`password` elements.

Basic auth using `token` syntax:

```yaml
  auth:
    basic:
      token: value
```

In this case, the `value` is used as Basic authentication token in the `Authorization` header: 
```
Authorization: Basic value
```

Basic auth using `username` and `password` syntax:

```yaml
  auth:
    basic:
      username: any_username
      password: any_password
```

In this example, `username` and `password` values will be formatted according
to standard basic authentication rules:
```
Authorization: Basic base64(username + ":" + password)
```

To avoid exposing credentials in your Concord file, replace the actual values
with usage of the [Crypto task](./crypto.html).

Use valid values for basic authentication parameters. Authentication failure
causes an `UnauthorizedException` error.

### Body

The HTTP method type `POST`, `PUT` and `PATCH` requires a `body` parameter that
contains a complex object (map), json sourced from a file, or raw string.

Body for request type `json`:

```yaml
  request: json
  body:
    myObject:
      nestedVar: 123
```

The HTTP task converts complex objects like the above into a string and passes
it into the body of the request. The converted string for the above example is
`{ "myObject": { "nestedVar": 123 } }`.

The HTTP task accepts raw JSON string, and throws an `incompatible request
type` error when it detects improper formatting.

Body for Request Type `file`:

```yaml
  request: file
  body: "relative_path/file.bin"
```

Failure to find file of the name given in the referenced location results in
a`FileNotFoundException` error.

Body for Request Type `string`:

```yaml
  request: string
  body: "sample string for body of post request"
```

### Headers

Extra header values can be specified using `headers` key:

```yaml
  headers:
    MyHeader: "a value"
    X-Some-Header: "..."
```

### Query Parameters

Query parameters can be specified using `query` key:

```yaml
  query:
    param: "Hello Concord"
    otherParam: "..."
```

Parameters are automatically encoded and appended to the request URL.

### Request Type

A specific request type in `request` is optional for `GET` method usage, but
mandatory for `POST` and `PUT`. It maps over to the `CONTENT-TYPE` header of the HTTP
request.

Types supported currently:

- `string` (converted into `text/plain`)
- `json` (converted into `application/json`)
- `file` (converted into `application/octet-stream`)
- `form` (converted into `application/x-www-form-urlencoded`)
- `formData` (converted into `multipart/form-data`)

### Response Type

`response` is an optional parameter that maps to the `ACCEPT` header of the HTTP
request.

Types supported currently:

- `string` (converted into `text/plain`)
- `json` (converted into `application/json`)
- `file` (converted into `application/octet-stream`)

### HTTP Task Response

In addition to
[common task result fields](../processes-v2/flows.html#task-result-data-structure),
the `http` task returns:

- `ok`: true if status code belongs to success family
- `error`: Descriptive error message from endpoint
- `content`: json/string response or relative path (for response type `file`)
- `headers`: key-value pairs of response headers
- `statusCode`: HTTP status codes

### Proxy Authentication

Proxy auth using `username` and `password` syntax:

```yaml
  proxyAuth:
    user: "user"
    password: "pass"
```

## Examples

Following are examples that illustrate the syntax usage for the HTTP task.

#### Full Syntax for GET or DELETE Requests

```yaml
- task: http
  in:
    method: GET # or DELETE
    url: "https://api.example.com:port/path/endpoint"
    response: json
  out: jsonResponse
- if: ${jsonResponse.ok}
  then:
   - log: "Response received: ${jsonResponse.content}"
```

#### Full Syntax for POST, PATCH or PUT Requests

Using a YAML object for the body:

```yaml
- task: http
  in:
    request: json
    method: POST # or PATCH or PUT
    url: "https://api.example.com:port/path/endpoint"
    body: 
      userObj:
        name: concord
    response: json
  out: jsonResponse
- if: ${jsonResponse.ok}
  then:
   - log: "Response received: ${jsonResponse.content}"
```

Using raw JSON for the body:

```yaml
- task: http
  in:
    request: json
    method: POST # `PATCH`, `PUT`
    url: "https://api.example.com:port/path/endpoint"
    body: |
      {
        "myObject": {
          "nestedVar": 123
        }
      }
    response: json
  out: jsonResponse
- if: ${jsonResponse.ok}
  then:
   - log: "Response received: ${jsonResponse.content}"
```

#### Full Syntax for Multipart Form

Using multipart form for the body

```yaml
- task: http
  in:
    request: formData
    method: POST
    url: "https://api.example.com:port/path/endpoint"
    body:
      field1: "string value" # text/plain part
      field2: "@myFile.txt" # application/octet-stream part with a filename
      field3:
        type: "text/plain"  # manually specify the content-type of the part
        data: "string value"
```

#### Full Syntax for Secure Request

Using Basic Authentication with an existing value:

```yaml
- task: http
  in:
    auth:
      basic:
        token: base64_encoded_token
    method: GET
    url: "https://api.example.com:port/path/endpoint"
    response: json
  out: jsonResponse
- if: ${jsonResponse.ok}
  then:
   - log: "Response received: ${jsonResponse.content}"
```

Using Basic Authentication with a username and a password: 

```yaml
- task: http
  in:
    auth:
      basic:
        username: username
        password: password
    method: GET
    url: "https://api.example.com:port/path/endpoint"
    response: json
  out: jsonResponse
- if: ${jsonResponse.ok}
  then:
   - log: "Response received: ${jsonResponse.content}"
```

<a name="proxy-usage"/>

#### Proxy Usage

```yaml
- task: http
  in:
    method: GET
    url: "https://api.example.com:port/path/endpoint"
    proxy: "http://proxy.example.com:8080"
    proxyAuth:
      user: username
      password: password
```
