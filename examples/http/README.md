# Http task
Http task currently support only Get and Post requests. It will execute the request and return the HttpTaskResponse object which can be store in the output variable by providing the 'out' argument. HttpTaskResponse will contains the following fields:
* success (true or false)
* content (json/string response or relative path for response type 'file')
* statusCode
* errorString (Descriptive error message from endpoint if error is true)

## Sample usage
### inline syntax
```yaml
- log: "${http.asString('http://host:post/path/test.txt')}"
```

### full syntax, saving response as a string value
```yaml
- task: http
  in:
    method: GET
    auth:
      basic:
        username: myUser
        password: myPass
    url: "http://host:post/path/endpoint"
    response: string # type of the response ('string', 'file', 'json')
    out: response # HttpTaskResponse object -> content field will contain the string response
- if: ${response.success} # HttpTaskResponse object
  then:
   - log: "Response received: ${response.content}" 
```
### full syntax, saving response as a local temporary file
```yaml
- task: http
  in:
    method: GET
    url: "http://host:post/path/endpoint"
    response: file
    out: fileResponse # HttpTaskResponse -> content field will contain the path of temp file
- if: ${fileResponse.success} # HttpTaskResponse object
  then:
   - log: "Response received: ${fileResponse.content}" # relative path which can be pass to POST request in body
```
### full syntax, POSTing JSON and parsing the response
```yaml
- task: http
  in:
    method: POST
    url: "http://host:post/path/endpoint"
    request: json # type of the request ('string', 'file', 'json') (Mandatory for POST)
    body: "${myJsonObject}" # will be serialized as JSON
    response: json
    out: jsonResponse # HttpTaskResponse -> content field will contain the parsed JSON response
- if: ${jsonResponse.success} # HttpTaskResponse object
  then:
   - log: "Response received: ${jsonResponse.content}"  
```