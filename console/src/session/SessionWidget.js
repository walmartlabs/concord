import React from "react";
import PropTypes from "prop-types";
import {Dropdown} from "semantic-ui-react";

const sessionWidget = ({displayName, onLogout}) => {
    const logOut = (ev) => {
        ev.preventDefault();
        onLogout();
    };

    return <Dropdown item text={displayName}>
        <Dropdown.Menu>
            <Dropdown.Item icon="log out" onClick={logOut} content="Log out"/>
        </Dropdown.Menu>
    </Dropdown>;
};

sessionWidget.propTypes = {
    displayName: PropTypes.string.isRequired,
    onLogout: PropTypes.func.isRequired
};

export default sessionWidget;
