import React from "react";
import {connect} from "react-redux";
import {Button, Modal, Message} from "semantic-ui-react";
import ErrorMessage from "../../shared/ErrorMessage";
import {actions as modal} from "../../shared/Modal";
import * as selectors from "./reducers";

export const MODAL_TYPE = "SHOW_SECRET_PUBLIC_KEY_POPUP";

const showSecretPublicKey = ({open, name, publicKeyError, getPublicKey, onCloseFn, inFlightFn}) => {
    const inFlight = inFlightFn(name);

    return ( 
        <Modal open={open} dimmer="inverted">
            <Modal.Header>{`Public Key for: ${name}`}</Modal.Header>
            <Modal.Content>
                { publicKeyError && <ErrorMessage message={ publicKeyError }/> }
                { !publicKeyError && (
                    <div>
                        <Message success 
                            style={{ overflowWrap: "break-word" }}>
                            {getPublicKey}
                        </Message>
                    </div>
                )}
            </Modal.Content>
            <Modal.Actions>
                <Button disabled={inFlight} onClick={onCloseFn}>Close</Button>
            </Modal.Actions>
        </Modal>
    );
}

showSecretPublicKey.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = ({ secretList }) => ({
    inFlightFn: (name) => selectors.isInFlight( secretList, name ),
    publicKeyError: selectors.getPublicKeyError( secretList ),
    getPublicKey: selectors.getPublicKey( secretList )
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close())
});

export default connect(mapStateToProps, mapDispatchToProps)(showSecretPublicKey);
