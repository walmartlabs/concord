import React from 'react';
import { Field, reduxForm } from 'redux-form';

import { Form, Card, Button, Segment } from 'semantic-ui-react';

const CreatePlainSecret = props => {
  const { handleSubmit, pristine, reset, submitting } = props;
  return (
    <Segment> 
      <h2>Create Plain Secret</h2>
      <form onSubmit={ handleSubmit }>
        <div>
          <label>Concord ID</label>
            <Field
              name="ConcordId"
              component={Form.Input}
              type="text"
              placeholder="Concord ID"
            />
        </div>
        <div>
          <label>Secret Phrase</label>
            <Field
              name="secret"
              component={Form.Input}
              type="text"
              placeholder="Plain Secret Text"
            />
        </div>
        {/* <div>
          <label>Generate Password</label>
            <Field
              name="generatePassword"
              component={Form.Checkbox}
              parse={ () }
            />
        </div>
        <div>
          <label>Store password</label>
          <Field
            name="storePassword"
            component={Form.Input}
            type="password"
            placeholder="Store Password"
          />
        </div>
         */}
        <div>
          <Button type="submit" disabled={pristine || submitting}>Submit</Button>
        </div>

      </form>
    </Segment>
  );
};

export default reduxForm({
  form: 'CreatePlainSecret',
})(CreatePlainSecret);
