# Form

The REST API provides a number of operations to work with
Concord [forms](../getting-started/forms.md):

- [List Current Forms](#list-current-forms)
- [Get Form Data](#get-form-data)
- [Submit JSON Data as Form Values](#submit-json-data-as-form-values)
- [Submit Multipart Data as Form Values](#submit-multipart-data-as-form-values)

## List Current Forms

Returns a list of currently available forms for a specific process.

* **URI** `/api/v1/process/${instanceId}/form`
* **Method** `GET`
* **Headers** `Authorization`
* **Parameters**
    ID of a process: `${instanceId}`
* **Body**
  none
* **Success response**
  ```
  Content-Type: application/json
  ```

  ```json
  [
    { "name":  "myForm", "custom": false, ... },
    { "name":  "myOtherForm", ... },
  ]
  ```

## Get Form Data

Returns data of a form, including the form's fields and their values.

* **URI** `/api/v1/process/${instanceId}/form/${formName}`
* **Method** `GET`
* **Headers** `Authorization`
* **Parameters**
    ID of a process: `${instanceId}`
    Name of the form: `${formName}`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "processInstanceId": "...",
      "name": "myForm",
      ...,
      "fields": [
        { "name": "...", "type": "..." }
      ]
    }
    ```

## Submit JSON Data as Form Values

Submits the provided JSON data as form values. The process resumes if the data
passes the validation.

* **URI** `/api/v1/process/${instanceId}/form/${formName}`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "myField": "myValue",
      ...
    }
    ```
    A JSON object where keys must match the form's field values.

* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true
    }
    ```
* **Validation errors response**

    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": false,
      "errors": {
        "myField": "..."
      }
    }
    ```

## Submit Multipart Data as Form Values

Submits the provided `multipart/form-data` request as form values. The process
resumes if the data passes the validation. This endpoint can be used to submit
`file` fields (upload a file).

Note the `multipart` extension in the endpoint's URL.

* **URI** `/api/v1/process/${instanceId}/form/${formName}/multipart`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: multipart/form-data`
* **Body**
    A `multipart/form-data` body where each part corresponds to one of
    the form's fields. 

* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true
    }
    ```
* **Validation errors response**

    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": false,
      "errors": {
        "myField": "..."
      }
    }
    ```
* **Example**
    ```
    curl -i -H "Authorization: ..." \
    -F myValue=abc \
    -F myFile=@form.yml \
    https://concord.example.com/api/v1/process/361bec22-14eb-4063-a26d-0eb7e6d4654e/form/myForm/multipart
    ```
