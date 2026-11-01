# Forms

Concord flows can provide simple web-based user interfaces with forms for data
input from users. Forms are described declaratively in
[Concord file](../processes-v1/flows.md) and optionally contain
[custom HTML/CSS/JS/etc resources](#custom-forms).

- [Form declaration](#declaration)
- [Form fields](#fields)
- [Form submitter](#submitter)
- [Using a form in a flow](#using)
- [Custom error messages](#error)
- [Custom forms](#custom)
- [Accessing form data](#access)
- [File upload](#upload)
- [Shared resources](#shared)
- [User access](#user)
- [Restricting forms](#restriction)
- [Dynamic forms](#dynamic)
- [Using API](#using-api)
- [Examples](#examples)

<a name="declaration"/>

## Form Declaration

Forms are declared at in the `forms` section of the Concord file:

```yaml
forms:
  myForm:
  - ...
```

The name of a form (in this example it's `myForm`) can be used to
[call a form](#using-a-form-in-a-flow) from a process. Also, it will be used
as a name of an object which will store the values of the fields.

Such form definitions can be reused multiple times in the same process.

Form fields can also be defined
[dynamically during the runtime of the process](#dynamic).

> **Note:** Form names can only contain alphanumerics, whitespaces, underscores(_) and dollar signs($).

<a name="fields"/>

## Form Fields

Forms must contain one or more fields:

```yaml
forms:
  myForm:
  - fullName: { label: "Name", type: "string", pattern: ".* .*", readonly: true, placeholder: "Place name here" }
  - age: { label: "Age", type: "int", min: 21, max: 100 }
  - favouriteColour: { label: "Favourite colour", type: "string", allow: ["gray", "grey"], search: true }
  - languages: { label: "Preferred languages", type: "string+", allow: "${locale.languages()}" }
  - password: { label: "Password", type: "string", inputType: "password" }
  - rememberMe: { label: "Remember me", type: "boolean" }
  - photo: { label: "Photo", type: "file" }
  - email: { label: "Email", type: "string", inputType: "email" }
```

Field declaration consists of the name (`myField`), the type
(`string`) and additional options.

The name of a field will be used to store a field's value in the
form's results. E.g. if the form's name is `myForm` and the field's
name is `myField`, then the value of the field will be stored in
`myForm.myField` variable.

Common options:
- `label`: the field's label, usually human-readable;
- `value`: default value [expression](#expressions), evaluated when
the form is called;
- `allow`: allowed value(s). Can be a YAML literal, array, object or an
[expression](#expressions).

Supported types of fields and their options:
- `string`: a string value
  - `pattern`: (optional) a regular expression to check the value.
  - `inputType`: (optional) specifies the `type` of html `<input>`
  element to display e.g. `text`, `button`, `checkbox` and others.
  - `readonly`: (optional) specifies that an input field is read-only.
  - `placeholder`: (optional) specifies a short hint that describes the expected value of an input field.
  - `search`: (optional) allows user to type and search item in dropdown input
- `int`: an integer value
  - `min`, `max`: (optional) value bounds.
  - `readonly`: (optional) specifies that an input field is read-only.
  - `placeholder`: (optional) specifies a short hint that describes the expected value of an input field.
- `decimal`: a decimal value
  - `min`, `max`: (optional) value bounds.
  - `readonly`: (optional) specifies that an input field is read-only.
  - `placeholder`: (optional) specifies a short hint that describes the expected value of an input field.
- `boolean`: a boolean value, `true` or `false`;
  - `readonly`: (optional) specifies that an input field is read-only.
- `file`: a file upload field, the submitted file is stored as a file in the
process' workspace. Find more tips in our [dedicated section](#upload).

Supported input types:
- `password`: provide a way for the user to securely enter a password.
- `email`: provide a way for the user to enter a correct email.

Cardinality of the field can be specified by adding a cardinality
quantifier to the type:
- a single non-optional value: `string`;
- optional value: `string?`;
- one or more values: `string+`;
- zero or more values: `string*`.

Additional field types will be added in the next versions.

<a name="submitter"/>

### Form Submitter

Concord can optionally store the form submitter's data in a `submittedBy`
variable. It can be enabled using `saveSubmittedBy` form call option:
```yaml
flows:
  default:
  - form: myForm
    saveSubmittedBy: true
    
  - log: "Hello, ${myForm.submittedBy.displayName}"
```

The variable has the same structure as `${initiator}` or `${currentUser}`
(see [Provided Variables](../processes-v1/index.md#provided-variables)
section).

<a name="using"/>

## Using a Form in a Flow

To call a form from a process, use `form` command:

```yaml
flows:
  default:
  - form: myForm
  - log: "Hello, ${myForm.myField}"
```

Expressions can also be used in form calls:

```yaml
configuration:
  arguments:
    formNameVar: "myForm"

flows:
  default:
  - form: ${formNameVar}
  - log: "Hello, ${myForm.name}"
  - log: "Hello, ${context.getVariable(formNameVar).name}"

forms:
  myForm:
    - name: { type: "string" }
```

Forms will be pre-populated with values if the current context
contains a map object, stored under the form's name. E.g. if the
context has a map object

```json
{
  "myForm": {
    "myField": "my string value"
  }
}
```

then the form's `myField` will be populated with `my string value`.

The `form` command accepts additional options:
```yaml
flows:
  default:
  - form: myForm
    yield: true
    values:
      myField: "a different value"
      additionalData:
        nestedField:
          aValue: 123
```

Supported options:

- `yield`: a boolean value. If `true`, the UI wizard will stop after
this form and the rest of the process will continue in the background.
Supported only for non-custom (without user HTML) forms;
- `values`: additional values, to override default form values or to
provide additional data;
- `fields`: allows defining the form fields at runtime, see more in the
  [Dynamic Forms](#dynamic) section.

<a name="error"/>

## Custom Error Messages

While Concord provides default error messages for form field validation, the error 
text that displays can be customized . With a form created from your YAML 
file, this can be accomplished with the addition of a `locale.properties` 
file in the same directory location.

The error types that can be customized are:
- `invalidCardinality`
- `expectedString`
- `expectedInteger`
- `expectedDecimal`
- `expectedBoolean`
- `doesntMatchPattern`
- `integerRangeError`
- `decimalRangeError`
- `valueNotAllowed`

To customize the same error message for all fields, the syntax is
simply the `error type:customized error`. A `locale.properties` 
file that looks like the following example flags all fields empty 
after submission with the error 'Required field':
```
invalidCardinality=Required field
```

For customizing specific fields in a form, use the format `fieldname.error type=custom 
message`. In a form to collect a name, phone number, and an optional email, the following
`locale.properties` file requires a name and phone number, and enforces a specific pattern 
for the phone number (specified in YAML).
```
username.invalidCardinality=Please enter your username
phonenumber.invalidCardinality=Please enter your phone number
phonenumber.doesntMatchPattern=Please enter your phone number with the format ###-###-####
```

<a name="custom"/>

## Custom Forms

Look and feel of a form can be changed by providing form's own HTML,
CSS, JavaScript and other resources.

For example, if we have a Concord file file with a single form:
```yaml
flows:
  default:
  - form: myForm
  - log: "Hello, ${myForm.name}"

forms:
  myForm:
  - name: {type: "string"}
```

then we can provide a custom HTML for this form by placing it into
`forms/myForm/index.html` file:
```
forms/
  myForm/
    index.html
```

When the form is activated, the server will redirect a user to the
`index.html` file.

Here's an example of how a `index.html` file could look like:
```html
<!doctype html>
<html lang="en">
<head>
    <title>My Form</title>
    <script src="data.js"></script> <!-- (1) -->
</head>
<body>

<h1>My Form</h1>

<script type="text/javascript">
    function handleSubmit(form) {
       form.action = data.submitUrl; // (2)
    }
</script>

<form method="post" onsubmit="handleSubmit(this)">
    <label>Name:</label>
    <input name="name"/>  <!-- (3) -->

    <button>Submit</button>
</form>

</body>
</html>
```

Let's take a closer look:
1. `data.js` is referenced - a JavaScript file which is generated by
the server when the form is opened. See the
[Accessing the data](#accessing-form-data) section for more details;
2. `submitUrl`, a value provided in `data.js`, used as a submit URL
of the form. For every instance of a form, the server provides a
unique URL;
3. a HTML input field added with the name same as the name of
`myForm` field.

Forms can use any external JavaScript library or a CSS resource. The
only mandatory part is to use provided `submitUrl` value.

Custom forms with file uploading fields must use
`enctype="multipart/form-data"`:
```html
<form method="post" enctype="multipart/form-data">
    <label>Photo:</label>
    <input name="photo" type="file"/>
    <button>Submit</button>
</form>
```

<a name="access"/>

## Accessing Form Data

When a custom form is opened, the server generates a `data.js` file.
It contains values of the fields, validation error and additional
metadata:
```javascript
data = {
  "success" : false,
  "processFailed" : false,
  "submitUrl" : "/api/service/custom_form/f5c0ab7c-72d8-42ee-b02e-26baea56f686/cc0beb01-b42c-4991-ae6c-180de2b672e5/continue",
  "fields" : [ "name" ],
  "definitions" : {
    "name": {
        "type": "string"
    }
  },
  "values" : {
    "name": "Concord"
  },
  "errors" : {
    "name": "Required value"
  }
};
```

The file defines a JavaScript object with the following fields:

- `success` - `false` if a form submit failed;
- `processFailed` - `true` if a process execution failed outside of
a form;
- `submitUrl` - automatically generated URL which should be used to
submit new form values and resume the process;
- `fields` - list of form field names in the order of their declaration in the
  Concord file;
- `definitions` - form field definitions. Each key represents a
field:
  - `type` - type of the field;
  - `label` - optional label, set in the form definition;
  - `cardinality` - required cardinality of the field's value;
  - `allow` - allowed value(s) of the field.
- `values` - current values of the form fields;
- `errors` - validation error messages.


<a name="upload"/>

## File Upload

Forms with `file` fields allow users to upload arbitrary files:

```yaml
forms:
  myForm:
  - myFile: { label: "Upload a text file", type: "file" }

flows:
  default:
  - form: myForm
  - log: "Path: ${myForm.myFile}"
  - log: "Content: ${resource.asString(myForm.myFile)}"
```

After the file is uploaded, the path to the file in the workspace is stored as
the field's value.

Typically, the server limits the maximum size of uploaded files. The exact limit
depends on the configuration of a particular environment.

Custom forms must use `<form enctype="multipart/form-data"/>` in order
to support file upload.

<a name="shared"/>

## Shared Resources

Custom forms can have shared resources (e.g. common scripts or CSS
files). Those resources should be put into `forms/shared` directory
of a process:
```
forms/
  myForm/
    index.html
  myOtherForm/
    image.png
    index.html
  shared/
    logo.png
    common.js
    common.css
```

Shared resources can be referenced by forms using relative path:
```html
<head>
    <script src="../shared/common.js"></script>
</head>
```

<a name="user"/>

## User Access

Forms can be accessed by a user in two different ways:
- through [the URL](../api/process.md#browser-link);
- by clicking on the _Wizard_ button on the Console's process
status page.

In both cases, users will be redirected from form to form until the
process finishes, an error occurs or until a form with `yield: true`
is reached.

<a name="restriction"/>

## Restricting Forms

Submitting a form can be restricted to a particular user or a group of
users. This can be used to, but is not limited to, create flows with approval
steps. You can configure a flow, where an action is required from a user that is
not the process' initiator.

Restricted forms can be submitted only by the specified user(s) or the membersos a
security group - e.g. configured in your Active Directory/LDAP setup.

To restrict a form to specific user(s), use the `runAs` attribute. Used with a
boolean variable, rendered as a checkbox, in the form, can change the flow
depending on the approval or disapproval from the authorized user defined in
`username`.

```yaml
flows:
  default:
  - form: approvalForm
    runAs:
      username: "expectedUsername"

  - if: ${approvalForm.approved}
    then:
    - log: "Approved =)"
    else:
    - log: "Rejected =("

forms:
  approvalForm:
  - approved: { type: boolean }
```
Multiple users can be specified under `username`:

```yaml
flows:
  default:
  - form: approvalForm
    runAs:
      username: 
       - "userA"
       - "userB"
```

Here's how a form can be restricted to specific AD/LDAP groups:

In most cases it is more practical to use groups of users to decide on the
authorization. This can be achieved with the `group` list specified as
attributes of the `ldap` parameter of `runAs`.

```yaml
- form: approvalForm
  runAs:
    ldap:
      - group: "CN=managers,.*"
      - group: "CN=project-leads,.*"
```

The `group` element is a list of regular expressions used to match
the user's groups. If there's at least one match - the user will be
allowed to submit the form.

By default, after the restricted form is submitted, the process continues to run
on behalf of the process initiator. If you need to continue the execution on
behalf of the user that submitted the form, you need to set the `keep` attribute
to `true`. The `currentUser.username` variable initially contains the value of
`initiator.username`. After the form with the `keep: true` configuration,
`currentUser` contains details from the user, who submitted the form.

```yaml
flows:
  default:
  - log: "Starting as ${currentUser.username}" # the same as ${initiator.username}

  - form: approvalForm
    runAs:
      username: "expectedUsername"
      keep: true

  - log: "Continuing as ${currentUser.username}" # the user that submitted the form

forms:
  approvalForm:
  - approved: { type: boolean }
```

<a name="dynamic"/>

## Dynamic Forms

Form fields can be declared directly at the form usage step, without creating a
form definition. Here's a complete example:

```yaml
flows:
  default:
  - form: myForm
    fields:
    - firstName: {type: "string"}
    - lastName: {type: "string"}
  - log: "Hello, ${myForm.firstName} ${myForm.lastName}"
```

The `fields` parameter expects a list of form field definitions just like the
regular `forms` section. The list of fields can be stored as a variable and
referenced using an expression:

```yaml
configuration:
  arguments:
    myFormFields:
    - firstName: {type: "string"}
    - lastName: {type: "string"}
flows:
  default:
  - form: myForm
    fields: ${myFormFields}
```

With the usage of a [script](./scripting.md), the fields can be set dynamically at
process runtime, resulting in a dynamic form. A number of examples are available
in the
[dynamic_form_fields project]({{site.concord_source}}tree/master/examples/dynamic_form_fields).

## Using API

Forms can be retrieved and submitted using [the REST API](../api/form.md).
A form can be submitted either by posting JSON data or by using
`multipart/form-data` requests which also support file upload.

## Examples

The Concord repository contains a couple of examples on how to use
custom and regular forms:

- [single form]({{site.concord_source}}tree/master/examples/forms)
- [custom form]({{site.concord_source}}tree/master/examples/custom_form)
- [custom form with no external dependencies]({{site.concord_source}}tree/master/examples/custom_form_basic)
- [custom form with dynamic fields]({{site.concord_source}}tree/master/examples/dynamic_forms)
- [approval-style flow]({{site.concord_source}}tree/master/examples/approval)

