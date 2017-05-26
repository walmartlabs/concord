import React, {Component} from "react";
import {connect} from "react-redux";
import {Button, Modal} from "semantic-ui-react";
import {actions as modal} from "../shared/Modal";

export const MODAL_TYPE = "DELETE_PROJECT_POPUP";

class DeleteProjectPopup extends Component {

    constructor(props) {
        super(props);
        this.state = {inFlight: false};
    }

    render() {
        const {open, onConfirmFn, onCloseFn} = this.props;

        const confirmFn = (ev) => {
            ev.preventDefault();
            this.setState({inFlight: true});
            onConfirmFn();
        };

        const {inFlight} = this.state;

        return <Modal open={open} dimmer="inverted">
            <Modal.Header>Delete the project?</Modal.Header>
            <Modal.Content>
                Are you sure you want to delete the project?
            </Modal.Content>
            <Modal.Actions>
                <Button color="green" disabled={inFlight} onClick={onCloseFn}>No</Button>
                <Button color="red" loading={inFlight} onClick={confirmFn}>Yes</Button>
            </Modal.Actions>
        </Modal>;
    }
}

DeleteProjectPopup.MODAL_TYPE = MODAL_TYPE;

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close())
});

export default connect(null, mapDispatchToProps)(DeleteProjectPopup);