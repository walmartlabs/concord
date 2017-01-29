import React, {Component, PropTypes} from "react";
import {Icon} from "semantic-ui-react";

class RefreshButton extends Component {

    render() {
        return <Icon link size="large" name="refresh" {...this.props}/>
    }
}

RefreshButton.propTypes = {
    loading: PropTypes.bool,
    onClick: PropTypes.func
};

export default RefreshButton;