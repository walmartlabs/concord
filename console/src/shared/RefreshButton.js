import React from "react";
import PropTypes from "prop-types";
import {Icon} from "semantic-ui-react";

const refreshButton = (props) =>
    <Icon link size="large" name="refresh" {...props}/>;

refreshButton.propTypes = {
    loading: PropTypes.bool,
    onClick: PropTypes.func
};

export default refreshButton;