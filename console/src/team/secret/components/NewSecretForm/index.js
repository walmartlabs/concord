import React from "react";
import {connect} from "react-redux";
import {formValueSelector, reduxForm} from "redux-form";
import {Dropdown as SUIDropdown, Form} from "semantic-ui-react";

import {secretTypes} from "../../create/constants";
import {Dropdown, Field, FileInput} from "../../../../shared/forms";
import * as v from "../../../../shared/validation";

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

const renderField = (secretTypeValue) => {
    switch (secretTypeValue) {
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
        default: {
            return `Unsupported secret type: ${secretTypeValue}`;
        }
    }
};

let NewSecretForm = ({handleSubmit, pristine, invalid, loading, submitting, secretTypeValue}) =>
    <Form onSubmit={handleSubmit} loading={loading || submitting}>
        <Field name="name" label="Name" required/>
        <Dropdown name="secretType" label="Type" widget={SecretTypeDropdown} required/>

        {renderField(secretTypeValue)}

        <Field name="storePassword" label="Store password"/>

        <Form.Button primary type="submit" disabled={pristine || submitting || invalid}>Submit</Form.Button>
    </Form>;

const validate = ({name, secretType, publicFile, privateFile, username, password, data}) => {
    const errors = {};

    errors.name = v.secret.name(name);

    switch (secretType) {
        case secretTypes.newKeyPair: {
            break;
        }
        case secretTypes.existingKeyPair: {
            errors.publicFile = v.secret.publicFile(publicFile);
            errors.privateFile = v.secret.privateFile(privateFile);
            break;
        }
        case secretTypes.usernamePassword: {
            errors.username = v.secret.username(username);
            errors.password = v.secret.password(password);
            break;
        }
        case secretTypes.singleValue: {
            errors.data = v.secret.data(data);
            break;
        }
        default: {
            errors.type = `Unsupported secret type: ${secretType}`;
            break;
        }
    }

    return errors;
};

const asyncValidate = ({name}, dispatch, {nameCheckFn}) => {
    if (!nameCheckFn) {
        return Promise.resolve(true);
    }

    return nameCheckFn(name);
};

NewSecretForm = reduxForm({
    form: "newSecretForm",
    initialValues: {secretType: secretTypes.newKeyPair},
    validate,
    asyncValidate,
    enableReinitialize: true,
    keepDirtyOnReinitialize: true
})(NewSecretForm);

const selector = formValueSelector("newSecretForm");

const mapStateToProps = (state) => ({
    secretTypeValue: selector(state, "secretType")
});

export default connect(mapStateToProps)(NewSecretForm);