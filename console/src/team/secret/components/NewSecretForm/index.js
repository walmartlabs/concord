import React from "react";
import {connect} from "react-redux";
import {formValueSelector, reduxForm} from "redux-form";
import {Dropdown as SUIDropdown, Form, Segment} from "semantic-ui-react";

import {Checkbox, Dropdown, Field, FileInput} from "../../../../shared/forms";
import * as v from "../../../../shared/validation";

const secretTypes = {
    newKeyPair: "NEW_KEY_PAIR",
    existingKeyPair: "EXISTING_KEY_PAIR",
    usernamePassword: "USERNAME_PASSWORD",
    singleValue: "DATA"
};

const SecretTypeDropdown = props => {
    const types = [
        {text: "New key pair", value: secretTypes.newKeyPair},
        {text: "Existing key pair", value: secretTypes.existingKeyPair},
        {text: "Username/password", value: secretTypes.usernamePassword},
        {text: "Single value", value: secretTypes.singleValue}
    ];
    return <SUIDropdown fluid selection
                        placeholder="Type"
                        options={types}
                        {...props}/>;
};

const renderField = (typeValue) => {
    switch (typeValue) {
        case secretTypes.newKeyPair: {
            return;
        }
        case secretTypes.existingKeyPair: {
            return <Form.Group unstackable widths={2}>
                <FileInput name="publicFile" label="Public key" required/>
                <FileInput name="privateFile" label="Private key" required/>
            </Form.Group>
        }
        case secretTypes.usernamePassword: {
            return <Form.Group unstackable widths={2}>
                <Field name="username" label="Username" required/>
                <Field name="password" label="Password" required/>
            </Form.Group>
        }
        case secretTypes.singleValue: {
            return <Field name="data" label="Value" required/>
        }
    }
};

let NewSecretForm = ({handleSubmit, pristine, invalid, submitting, typeValue}) =>

    <Form onSubmit={handleSubmit} loading={submitting}>
        <Field name="name" label="Name" required/>
        <Dropdown name="type" label="Type" widget={SecretTypeDropdown} required/>

        {renderField(typeValue)}

        <Field name="storePassword" label="Store password"/>

        <Form.Button primary type="submit" disabled={pristine || submitting || invalid}>Submit</Form.Button>
    </Form>;

const validate = ({name, type, publicFile, privateFile, username, password, data}) => {
    const errors = {};

    errors.name = v.secret.name(name);

    switch (type) {
        case secretTypes.existingKeyPair: {
            errors.publicFile = v.secret.publicFile(publicFile);
            errors.privateFile = v.secret.privateFile(privateFile);
        }
        case secretTypes.usernamePassword: {
            errors.username = v.secret.username(username);
            errors.password = v.secret.password(password);
        }
        case secretTypes.singleValue: {
            errors.data = v.secret.data(data);
        }
    }

    return errors;
};

NewSecretForm = reduxForm({
    form: "NewSecretForm",
    initialValues: {type: secretTypes.newKeyPair},
    validate,
    enableReinitialize: true,
    keepDirtyOnReinitialize: true
})(NewSecretForm);

const selector = formValueSelector("NewSecretForm");

const mapStateToProps = (state) => ({
    typeValue: selector(state, "type")
});

export default connect(mapStateToProps)(NewSecretForm);