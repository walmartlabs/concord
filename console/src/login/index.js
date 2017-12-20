/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
import React from "react";
import {connect} from "react-redux";
import {reduxForm} from "redux-form";
import {Dimmer, Form, Image, Loader, Message, Card, CardContent} from "semantic-ui-react";
import {Field} from "../shared/forms";
import * as actions from "./actions";
import reducers, * as selectors from "./reducers";
import sagas from "./sagas";
import "./styles.css";

const isThere = (x) => x !== undefined && x !== null;

let loginForm = (props) => {
    const {apiError, loading, handleSubmit} = props;
    return (
        <div className="flexbox-container">
            <Card centered>
                <CardContent>
                    <div className="crop">
                        <Image id="strati-logo" src="/strati-logo.png" size="medium"/>
                    </div>
                    <Dimmer active={loading} inverted>
                        <Loader/>
                    </Dimmer>

                    <Form error={isThere(apiError)} onSubmit={handleSubmit}>

                        <Field name="username" label="Username" icon="user" required/>
                        <Field name="password" label="Password" type="password" icon="lock" required/>

                        <Message error content={apiError}/>
                        <Form.Button primary fluid>Login</Form.Button>
                    </Form>
                </CardContent>
            </Card>
        </div>
    );
};

loginForm = reduxForm({form: "login"})(loginForm);

const mapStateToProps = ({login}) => ({
    apiError: selectors.getError(login),
    loading: selectors.isSubmitting(login)
});

const mapDispatchToProps = (dispatch) => ({
    onSubmit: ({username, password}) => dispatch(actions.doLogin(username, password))
});

// re-export public stuff
export {actions, reducers, selectors, sagas};

export default connect(mapStateToProps, mapDispatchToProps)(loginForm);
