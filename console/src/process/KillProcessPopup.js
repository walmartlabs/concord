import React from "react";
import {connect} from "react-redux";
import {Button, Modal} from "semantic-ui-react";
import {actions as modal} from "../shared/Modal";
import * as actions from "./actions";
import * as selectors from "./reducers";

export const MODAL_TYPE = "KILL_PROCESS_POPUP";

const killProcessPopup = ({open, instanceId, onSuccess, onCloseFn, onConfirmFn, inFlightFn}) => {
    const inFlight = inFlightFn(instanceId);

    return <Modal open={open} dimmer="inverted">
        <Modal.Header>Cancel the selected process?</Modal.Header>
        <Modal.Content>
            Are you sure you want to cancel the selected process?
        </Modal.Content>
        <Modal.Actions>
            <Button color="green" disabled={inFlight} onClick={onCloseFn}>No</Button>
            <Button color="red" loading={inFlight} onClick={() => onConfirmFn(instanceId, onSuccess)}>Yes</Button>
        </Modal.Actions>
    </Modal>;
}

killProcessPopup.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = (state) => ({
    inFlightFn: (instanceId) => selectors.isInFlight(state.process, instanceId)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close()),
    onConfirmFn: (instanceId, onSuccess) => {
        if (!onSuccess) {
            onSuccess = [];
        }

        // first, we need to close the dialog
        onSuccess.unshift(modal.close());

        dispatch(actions.kill(instanceId, onSuccess))
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(killProcessPopup);