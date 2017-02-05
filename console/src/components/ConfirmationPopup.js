import React, {Component, PropTypes} from "react";
import {Icon, Button, Modal} from "semantic-ui-react";

class ConfirmationPopup extends Component {
    state = {open: false};

    handleOpen = () => {
        this.setState({open: true});
    };

    handleClose = () => {
        this.setState({open: false});
    };

    handleConfirm = () => {
        this.setState({open: false});
        this.props.onConfirmFn();
    };

    render = () => {
        const {open} = this.state;
        const {disabled, icon, message} = this.props;

        return <Modal open={open} trigger={<Button icon={icon || "delete"} color="red"
                                                   onClick={this.handleOpen}
                                                   disabled={disabled}
                                                   loading={disabled}/>}>

            <Modal.Header>{message}</Modal.Header>
            <Modal.Actions>
                <Button basic onClick={this.handleClose}>
                    <Icon name="remove"/> Cancel
                </Button>
                <Button color="red" onClick={this.handleConfirm}>
                    <Icon name="checkmark"/> Ok
                </Button>
            </Modal.Actions>
        </Modal>;
    };
}

ConfirmationPopup.propTypes = {
    disabled: PropTypes.bool,
    icon: PropTypes.string,
    message: PropTypes.string.isRequired,
    onConfirmFn: PropTypes.func.isRequired
};

export default ConfirmationPopup;