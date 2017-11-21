import React from "react";
import {connect} from "react-redux";
import {Button, Modal, TextArea} from "semantic-ui-react";
import {CopyToClipboard} from "react-copy-to-clipboard";

import ErrorMessage from "../../../shared/ErrorMessage";
import {actions as modal} from "../../../shared/Modal";
import * as selectors from "./reducers";

import "./styles.css";

export const MODAL_TYPE = "SHOW_SECRET_PUBLIC_KEY_POPUP";

const showSecretPublicKey = ({open, teamName, name, error, publicKey, onCloseFn, inFlightFn}) => {
    const inFlight = inFlightFn(teamName, name);

    return <Modal open={open} dimmer="inverted">
        <Modal.Header>{name}</Modal.Header>
        <Modal.Content>
            {error && <ErrorMessage message={error}/>}
            {publicKey && <div>
                <b>Public key: </b>
                <CopyToClipboard text={publicKey}>
                    <Button icon="copy" size="mini" basic/>
                </CopyToClipboard>
                <TextArea id="publicKeyValue" className="secretData">{publicKey}</TextArea>
            </div>}
        </Modal.Content>
        <Modal.Actions>
            <Button disabled={inFlight} onClick={onCloseFn}>Close</Button>
        </Modal.Actions>
    </Modal>;
};

showSecretPublicKey.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = ({secretList}) => ({
    inFlightFn: (teamName, name) => selectors.isInFlight(secretList, teamName, name),
    error: selectors.getPublicKeyError(secretList),
    publicKey: selectors.getPublicKey(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close())
});

export default connect(mapStateToProps, mapDispatchToProps)(showSecretPublicKey);
