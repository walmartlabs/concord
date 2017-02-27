import React, {Component} from "react";
import {Grid, Form, Message} from "semantic-ui-react";
import {reduxForm, Field} from "redux-form";

const CustomInput = ({input, meta: {error, touched}, label, ...custom}) => (
    <Form.Input iconPosition="left" placeholder={label} error={error && touched} {...input} {...custom}/>
);

class VisibleLoginForm extends Component {

    render() {
        const {handleSubmit, doLogin, invalid, error, submitting} = this.props;

        return <Grid centered verticalAlign="middle" className="maxHeight tight">
            <Grid.Column width={3}>
                <Form error={invalid} onSubmit={handleSubmit(doLogin)}>
                    <Field name="username" label="Username" component={CustomInput} icon="user"/>
                    <Field name="password" label="Password" component={CustomInput} icon="lock" type="password"/>
                    <Form.Button fluid primary type="submit" loading={submitting} disabled={submitting}>
                        Log in
                    </Form.Button>
                    <Message error content={error}/>
                </Form>
            </Grid.Column>
        </Grid>;
    }
}

export default reduxForm({form: "login"})(VisibleLoginForm);

