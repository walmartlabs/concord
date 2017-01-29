import React, {Component, PropTypes} from "react";
import {Message, Icon, Button} from "semantic-ui-react";
import "./ErrorMessage.css";

class ErrorMessage extends Component {
    render() {
        const {message, retryFn} = this.props;
        return <Message negative icon className="errorMessage">
            <Icon name="warning sign"/>
            <p>{message}</p>
            { retryFn && <Button basic onClick={retryFn}>Retry</Button> }
        </Message>;
    }
}

ErrorMessage.propTypes = {
    message: PropTypes.string.isRequired,
    retryFn: PropTypes.func
};

export default ErrorMessage;