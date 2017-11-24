import React, {Component} from "react";
import {connect} from "react-redux";
import {Button, Header, Message, TextArea} from "semantic-ui-react";
import {CopyToClipboard} from "react-copy-to-clipboard";

import ErrorMessage from "../../../shared/ErrorMessage";
import {NewSecretForm} from "../components";
import * as api from "./api";
import {actions, reducers, sagas, selectors} from "./effects";
import {getCurrentTeamName} from "../../../session/reducers";

import "./styles.css";

class CreateSecretPage extends Component {
    componentDidMount() {
        this.props.resetFn();
    }

    static renderResponse({password, publicKey}) {
        return <Message success>
            <Message.Header>Secret created</Message.Header>

            {publicKey && <div>
                <b>Public key: </b>
                <CopyToClipboard text={publicKey}>
                    <Button icon="copy" size="mini" basic/>
                </CopyToClipboard>
                <TextArea id="publicKeyValue" className="secretData">{publicKey}</TextArea>
            </div>}

            {password && <div>
                <b>Export password: </b>
                <CopyToClipboard text={password}>
                    <Button icon="copy" size="mini" basic/>
                </CopyToClipboard>
                <TextArea id="passwordValue" className="secretData">{password}</TextArea>
            </div>}
        </Message>;
    }

    render() {
        const {submitFn, teamName, response, error, ...rest} = this.props;

        const nameCheckFn = (secretName) => {
            return api.exists(teamName, secretName).then(exists => {
                if (exists) {
                    const err = {name: "Already exists"};
                    throw err;
                }
            });
        };

        return <div>
            <Header as="h3">New secret</Header>

            {error && <ErrorMessage message={error}/>}
            {response && response.ok && CreateSecretPage.renderResponse(response)}

            <NewSecretForm onSubmit={(req) => submitFn(teamName, req)}
                           nameCheckFn={nameCheckFn}
                           {...rest}/>
        </div>;
    }
}

const mapStateToProps = ({session, secretForm}) => ({
    teamName: getCurrentTeamName(session),
    response: selectors.response(secretForm),
    error: selectors.error(secretForm),
    loading: selectors.loading(secretForm)
});

const mapDispatchToProps = (dispatch) => ({
    resetFn: () => dispatch(actions.reset()),
    submitFn: (teamName, req) => dispatch(actions.createNewSecret(teamName, req))
});

export {reducers, sagas};

export default connect(mapStateToProps, mapDispatchToProps)(CreateSecretPage);