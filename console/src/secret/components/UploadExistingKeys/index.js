import React from 'react';
import { Field, reduxForm } from 'redux-form';

import { Form, Card, Button, Segment } from 'semantic-ui-react';

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
          <label>Public Key</label>
            <Field
              name="public"
              component={Form.Input}
              type="file"
              placeholder="Public Key"
            />
        </div>

        <div>
          <label>Private Key</label>
            <Field
              name="private"
              component={Form.Input}
              type="file"
              placeholder="Private Key"
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
