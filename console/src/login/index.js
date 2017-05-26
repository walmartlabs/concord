import React from "react";
import {connect} from "react-redux";
import {reduxForm} from "redux-form";
import {Dimmer, Form, Grid, Image, Loader, Message, Segment} from "semantic-ui-react";
import {Field} from "../shared/forms";
import * as actions from "./actions";
import reducers from "./reducers";
import * as selectors from "./reducers";
import sagas from "./sagas";

const isThere = (x) => x !== undefined && x !== null;

let loginForm = (props) => {
    const {error, loading, handleSubmit} = props;

    return <Grid centered verticalAlign="middle" className="maxHeight">
        <Grid.Column width="4" textAlign="left">
            <Segment>

                <Image src='/strati-logo.png' size='small' centered/>

                <Dimmer active={loading} inverted>
                    <Loader/>
                </Dimmer>

                <Form error={isThere(error)} onSubmit={handleSubmit}>

                    <Field name="username" label="Username" icon="user" required/>
                    <Field name="password" label="Password" type="password" icon="lock" required/>

                    <Message error content={error}/>
                    <Form.Button primary fluid>Login</Form.Button>
                </Form>
            </Segment>
        </Grid.Column>
    </Grid>;
};

loginForm = reduxForm({form: "login"})(loginForm);

const mapStateToProps = ({login}) => ({
    error: selectors.getError(login),
    loading: selectors.isSubmitting(login)
});

const mapDispatchToProps = (dispatch) => ({
    onSubmit: ({username, password}) => dispatch(actions.doLogin(username, password))
});

// re-export public stuff
export {actions, reducers, selectors, sagas};

export default connect(mapStateToProps, mapDispatchToProps)(loginForm);
