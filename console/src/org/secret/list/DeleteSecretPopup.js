import React from "react";
import {connect} from "react-redux";
import {Button, Modal} from "semantic-ui-react";

import ErrorMessage from "../../../shared/ErrorMessage";
import {actions as modal} from "../../../shared/Modal";

import {actions, selectors} from "./effects";

export const MODAL_TYPE = "DELETE_SECRET_POPUP";

const deleteSecretPopup = ({open, orgName, name, error, onSuccess, onCloseFn, onConfirmFn, inFlightFn}) => {
    const inFlight = inFlightFn(orgName, name);
    const onYesClick = () => onConfirmFn(orgName, name, onSuccess);

    return <Modal open={open} dimmer="inverted">
        <Modal.Header>Delete the selected secret?</Modal.Header>
        <Modal.Content>
            {error && <ErrorMessage message={error}/> }
            {!error && "Are you sure you want to delete the selected secret?" }
        </Modal.Content>
        <Modal.Actions>
            <Button color="green" disabled={inFlight} onClick={onCloseFn}>No</Button>
            <Button color="red" loading={inFlight} onClick={onYesClick}>Yes</Button>
        </Modal.Actions>
    </Modal>;
}

deleteSecretPopup.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = ({session, secretList}) => ({
    error: selectors.getDeleteError(secretList),
    inFlightFn: (orgName, name) => selectors.isInFlight(secretList, orgName, name)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close()),
    onConfirmFn: (orgName, name, onSuccess) => {
        if (!onSuccess) {
            onSuccess = [];
        }

        // first, we need to close the dialog
        onSuccess.unshift(modal.close());

        dispatch(actions.deleteSecret(orgName, name, onSuccess))
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(deleteSecretPopup);
