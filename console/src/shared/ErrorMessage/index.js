import React from "react";
import PropTypes from "prop-types";
import {Button, Icon, Message} from "semantic-ui-react";
import "./styles.css";

const errorMessage = ({message, retryFn}) =>
    <Message negative icon className="errorMessage">
        <Icon name="warning sign"/>
        <p>{message}</p>
        { retryFn && <Button basic onClick={retryFn}>Retry</Button> }
    </Message>;

errorMessage.propTypes = {
    message: PropTypes.string.isRequired,
    retryFn: PropTypes.func
};

export default errorMessage;