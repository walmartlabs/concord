import React from "react";
import {connect} from "react-redux";
import {Header, Message} from "semantic-ui-react";

import ErrorMessage from "../../../shared/ErrorMessage";
import {NewSecretForm} from "../components";
import * as api from "./api";
import {actions, reducers, sagas, selectors} from "./effects";
import {getCurrentTeamName} from "../../../session/reducers";

const renderResponse = ({ok, id, publicKey}) =>
    <Message success style={{overflowWrap: "break-word", fontFamily: "monospace"}}
             header={"Secret created"}
             content={publicKey ? `Public key: ${publicKey}` : "Secret was successfully created."}/>;

const CreateSecretPage = ({submitFn, teamName, response, error, ...rest}) => {
    const nameCheckFn = (secretName) => {
        return api.exists(teamName, secretName).then(exists => {
            if (exists) {
                throw {name: "Already exists"};
            }
        });
    };

    return <div>
        <Header as="h3">New secret</Header>

        {error && <ErrorMessage message={error}/>}
        {response && response.ok && renderResponse(response)}

        <NewSecretForm onSubmit={(req) => submitFn(teamName, req)}
                       nameCheckFn={nameCheckFn}
                       {...rest}/>
    </div>;
}

const mapStateToProps = ({session, teamSecretForm}) => ({
    teamName: getCurrentTeamName(session),
    response: selectors.response(teamSecretForm),
    error: selectors.error(teamSecretForm),
    loading: selectors.loading(teamSecretForm)
});

const mapDispatchToProps = (dispatch) => ({
    submitFn: (teamName, req) => dispatch(actions.createNewSecret(teamName, req))
});

export {reducers, sagas};

export default connect(mapStateToProps, mapDispatchToProps)(CreateSecretPage);