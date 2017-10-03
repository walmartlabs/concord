import React from 'react';
import { Field, reduxForm } from 'redux-form';

import { Form, Segment } from 'semantic-ui-react';

const CreatePlainSecret = props => {
  const { handleSubmit, pristine, submitting } = props;
  return (
    <Segment>
      <h2>Create Plain Secret</h2>
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
          <label>Secret Phrase</label>
          <Field
            name="secret"
            component={Form.Input}
            type="text"
            placeholder="Plain Secret Text"
          />
        </Form.Field>
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
        <br></br>
        <Form.Button primary type="submit" disabled={pristine || submitting}>Submit</Form.Button>

      </Form>
    </Segment>
  );
};

export default reduxForm({
  form: 'CreatePlainSecret',
})(CreatePlainSecret);
