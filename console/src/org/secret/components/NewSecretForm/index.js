/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
import React from 'react';
import { connect } from 'react-redux';
import { formValueSelector, reduxForm } from 'redux-form';
import { Dropdown as SUIDropdown, Form } from 'semantic-ui-react';

import { secretTypes, storePwdTypes } from '../../create/constants';
import { Dropdown, Field, FileInput } from '../../../../shared/forms';
import * as v from '../../../../shared/validation';

const SecretTypeDropdown = (props) => {
  const types = [
    { text: 'Generate a new key pair', value: secretTypes.newKeyPair },
    { text: 'Existing key pair', value: secretTypes.existingKeyPair },
    { text: 'Username/password', value: secretTypes.usernamePassword },
    { text: 'Single value', value: secretTypes.singleValue }
  ];
  return <SUIDropdown fluid selection placeholder="Type" options={types} {...props} />;
};

const StorePwdTypeDropdown = (props) => {
  const types = [
    { text: "N/A (encrypt using the server's key)", value: storePwdTypes.doNotUse },
    { text: 'Specify a password', value: storePwdTypes.specify },
    { text: 'Generate a new password', value: storePwdTypes.generate }
  ];
  return <SUIDropdown fluid selection placeholder="Store password" options={types} {...props} />;
};

const renderDataFields = (secretTypeValue) => {
  switch (secretTypeValue) {
    case secretTypes.newKeyPair: {
      return;
    }
    case secretTypes.existingKeyPair: {
      return (
        <Form.Group unstackable widths={2}>
          <FileInput name="publicFile" label="Public key" required />
          <FileInput name="privateFile" label="Private key" required />
        </Form.Group>
      );
    }
    case secretTypes.usernamePassword: {
      return (
        <Form.Group unstackable widths={2}>
          <Field name="username" label="Username" required />
          <Field name="password" label="Password" required />
        </Form.Group>
      );
    }
    case secretTypes.singleValue: {
      return <Field name="data" label="Value" required />;
    }
    default: {
      return `Unsupported secret type: ${secretTypeValue}`;
    }
  }
};

const renderStorePwdField = (storePwdTypeValue) => {
  switch (storePwdTypeValue) {
    case storePwdTypes.doNotUse:
    case storePwdTypes.generate: {
      return;
    }
    case storePwdTypes.specify: {
      return <Field name="storePassword" label="Specify a store password" required />;
    }
    default: {
      return `Unsupported store password type: ${storePwdTypeValue}`;
    }
  }
};

const visibilityField = () => {
  const visibilityOptions = [
    {
      text: 'Public',
      value: 'PUBLIC',
      description: 'Public secrets can be used by any user.',
      icon: 'unlock'
    },
    {
      text: 'Private',
      value: 'PRIVATE',
      description: "Private secrets can be used only by their organization's teams.",
      icon: 'lock'
    }
  ];
  return <Dropdown name="visibility" label="Visibility" options={visibilityOptions} required />;
};

let NewSecretForm = ({
  handleSubmit,
  pristine,
  invalid,
  loading,
  submitting,
  secretTypeValue,
  storePwdTypeValue
}) => (
  <Form onSubmit={handleSubmit} loading={loading || submitting}>
    <Field name="name" label="Name" required />

    {visibilityField()}

    <Dropdown name="secretType" label="Type" widget={SecretTypeDropdown} required />

    {renderDataFields(secretTypeValue)}

    <Dropdown name="storePwdType" label="Store password" widget={StorePwdTypeDropdown} />

    {renderStorePwdField(storePwdTypeValue)}

    <Form.Button primary type="submit" disabled={pristine || submitting || invalid}>
      Submit
    </Form.Button>
  </Form>
);

const validate = ({
  name,
  secretType,
  publicFile,
  privateFile,
  username,
  password,
  data,
  storePwdType,
  storePassword
}) => {
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

  if (storePwdType === storePwdTypes.specify) {
    errors.storePassword = v.secret.storePassword(storePassword);
  }

  return errors;
};

const asyncValidate = ({ name }, dispatch, { nameCheckFn }) => {
  if (!nameCheckFn) {
    return Promise.resolve(true);
  }

  return nameCheckFn(name);
};

NewSecretForm = reduxForm({
  form: 'newSecretForm',
  initialValues: {
    visibility: 'PUBLIC',
    secretType: secretTypes.newKeyPair,
    storePwdType: storePwdTypes.doNotUse
  },
  validate,
  asyncValidate,
  enableReinitialize: true,
  keepDirtyOnReinitialize: true
})(NewSecretForm);

const selector = formValueSelector('newSecretForm');

const mapStateToProps = (state) => ({
  secretTypeValue: selector(state, 'secretType'),
  storePwdTypeValue: selector(state, 'storePwdType')
});

export default connect(mapStateToProps)(NewSecretForm);
