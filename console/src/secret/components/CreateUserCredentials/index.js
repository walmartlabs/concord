import React from 'react';
import { Field, reduxForm } from 'redux-form';

import { Form, Segment } from 'semantic-ui-react';

const CreateUserCredentials = props => {
  const { handleSubmit, pristine, submitting } = props;
  return (
    <Segment>
      <h2>Create User Credentials</h2>
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

        <Form.Field required>
          <label>Username</label>
          <Field
            name="username"
            component={Form.Input}
            type="text"
            placeholder="UserName"
          />
        </Form.Field>

        <Form.Field required>
          <label>Password</label>
          <Field
            name="password"
            component={Form.Input}
            type="password"
            placeholder="Password"
          />
        </Form.Field>

        <br></br>
        <Form.Button primary type="submit" disabled={pristine || submitting}>Submit</Form.Button>

      </Form>
    </Segment>
  );
};

export default reduxForm({
  form: 'CreateUserCredentials',
})(CreateUserCredentials);
