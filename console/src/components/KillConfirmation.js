import React, {Component, PropTypes} from "react";
import {Icon, Button, Modal} from "semantic-ui-react";

class KillConfirmation extends Component {
    state = {open: false};

    handleOpen = () => {
        this.setState({open: true});
    };

    handleClose = () => {
        this.setState({open: false});
    };

    handleConfirm = () => {
        this.setState({open: false, blocked: true});
        this.props.onConfirmFn();
    };

    render = () => {
        const {open, blocked} = this.state;
        return <Modal open={open} trigger={<Button icon="delete" color="red"
                                                   onClick={this.handleOpen}
                                                   disabled={blocked}
                                                   loading={blocked}/>}>

            <Modal.Header>Kill the selected process?</Modal.Header>
            <Modal.Actions>
                <Button basic onClick={this.handleClose}>
                    <Icon name="remove"/> No
                </Button>
                <Button color="red" onClick={this.handleConfirm}>
                    <Icon name="checkmark"/> Yes
                </Button>
            </Modal.Actions>
        </Modal>;
    };
}

KillConfirmation.propTypes = {
    onConfirmFn: PropTypes.func.isRequired
};

export default KillConfirmation;