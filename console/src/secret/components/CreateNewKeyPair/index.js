import React from 'react';
import { Field, reduxForm } from 'redux-form';

import { Form, Segment } from 'semantic-ui-react';

const CreateNewKeyPair = props => {
    const { handleSubmit, pristine, submitting } = props;
    return (
        <Segment>
            <h2>Create New Key Pair</h2>
            <Form onSubmit={handleSubmit}>

                <Form.Field required>
                    <label>Concord ID</label>
                    <Field
                        required
                        name="ConcordId"
                        component={Form.Input}
                        type="text"
                        placeholder="Concord ID"
                    />
                </Form.Field>
                {/* <div>
                <label>Generate Password</label>
                <Field
                    name="generatePassword"
                    component={Form.Checkbox}
                    type="checkbox"
                />
            </div> */}

                {/* <div>
                <label>Store password</label>
                <Field
                    name="storePassword"
                    component={Form.Input}
                    type="password"
                    placeholder="Store Password"
                />
            </div> */}
                <br></br>
                <Form.Button primary type="submit" disabled={pristine || submitting}>Submit</Form.Button>

            </Form>
        </Segment>
    );
};

export default reduxForm({
    form: 'CreateNewKeyPair',
})(CreateNewKeyPair);
