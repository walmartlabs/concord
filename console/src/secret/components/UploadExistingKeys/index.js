import React from 'react';
import { Field, reduxForm } from 'redux-form';
import FileInput from "../FileInput";

import { Form, Segment } from 'semantic-ui-react';

const UploadExistingKeys = props => {
  const { handleSubmit, pristine, submitting } = props;
  return (
    <Segment> 
      <h2>Upload Existing Keys</h2>
      <Form onSubmit={handleSubmit}>
        
        <Form.Field required>
          <label>Concord ID</label>
            <Field
              name="ConcordId"
              component={Form.Input}
              type="text"
              placeholder="Concord ID"
            />
        </Form.Field>

        {/* 
            Styling File Inputs
            http://jsfiddle.net/Dr_Dev/2nu1ngk5/ 
        */}

        <Form.Field required>
          <label>Public Key: </label>
            <Field
              name="publicKey"
              component={ FileInput }
            />
        </Form.Field>

        <Form.Field required>
          <label>Private Key: </label>
            <Field
              name="privateKey"
              component={ FileInput }
            />
        </Form.Field>

        <br></br>
        <Form.Button primary type="submit" disabled={pristine || submitting}>Submit</Form.Button>

      </Form>
    </Segment>
  );
};

export default reduxForm({
  form: 'UploadExistingKeys',
})(UploadExistingKeys);
