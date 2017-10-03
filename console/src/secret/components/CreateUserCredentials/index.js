import React from 'react';
import { Field, reduxForm } from 'redux-form';

import { Form, Card, Button, Segment } from 'semantic-ui-react';

const CreateUserCredentials = props => {
  const { handleSubmit, pristine, reset, submitting } = props;
  return (
    <Segment> 
      <h2>Create User Credentials</h2>
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

        <div>
          <label>Username</label>
            <Field
              name="username"
              component={Form.Input}
              type="text"
              placeholder="UserName"
            />
        </div>

        <div>
          <label>Password</label>
            <Field
              name="password"
              component={Form.Input}
              type="password"
              placeholder="Password"
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
  form: 'CreateUserCredentials',
})(CreateUserCredentials);
