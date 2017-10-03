import React from 'react';
import { Field, reduxForm } from 'redux-form';
import FileInput from "../FileInput";

import { Form, Card, Button, Segment } from 'semantic-ui-react';

const customFileInput = (field) => {
  delete field.input.value; // <-- just delete the value property
  return <input type="file" id="file" {...field.input} />;
};

const UploadExistingKeys = props => {
  const { handleSubmit, pristine, reset, submitting } = props;
  return (
    <Segment> 
      <h2>Upload Existing Keys</h2>
      <form onSubmit={handleSubmit}>
        
        <div>
          <label>Concord ID</label>
            <Field
              name="ConcordId"
              component={Form.Input}
              type="text"
              placeholder="Concord ID"
            />
        </div>

        {/* 
            Styling File Inputs
            http://jsfiddle.net/Dr_Dev/2nu1ngk5/ 
        */}

        <div>
          <label>Public Key: </label>
            <Field
              name="publicKey"
              component={ FileInput }
            />
        </div>

        <div>
          <label>Private Key: </label>
            <Field
              name="privateKey"
              component={ FileInput }
            />
        </div>

        <div>
          <Button type="submit" disabled={pristine || submitting}>Submit</Button>
        </div>

      </form>
    </Segment>
  );
};

export default reduxForm({
  form: 'UploadExistingKeys',
})(UploadExistingKeys);
