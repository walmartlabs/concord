import React from "react";
import {connect} from "react-redux";
import {Control, LocalForm} from "react-redux-form";
import {Dimmer, Form, Grid, Loader, Message, Segment} from "semantic-ui-react";
import * as actions from "./actions";
import reducers from "./reducers";
import * as selectors from "./reducers";
import sagas from "./sagas";

const isThere = (x) => x !== undefined && x !== null;

const loginForm = ({error, submitting, doLogin}) =>
    <Grid centered verticalAlign="middle" className="maxHeight">
        <Grid.Column width="4" textAlign="left">
            <Segment>
                <Dimmer active={submitting} inverted>
                    <Loader/>
                </Dimmer>

                <LocalForm component={Form}
                           error={isThere(error)}
                           onSubmit={({username, password}) => doLogin(username, password)}>

                    <Control component={Form.Input} label="Username" model=".username" required/>
                    <Control component={Form.Input} label="Password" model=".password" type="password" required/>

                    <Message error content={error}/>
                    <Form.Button primary fluid>Login</Form.Button>
                </LocalForm>
            </Segment>
        </Grid.Column>
    </Grid>;

const mapStateToProps = ({login}) => ({
    error: selectors.getError(login),
    submitting: selectors.isSubmitting(login)
});

// re-export public stuff
export {actions, reducers, selectors, sagas};

export default connect(mapStateToProps, actions)(loginForm);
