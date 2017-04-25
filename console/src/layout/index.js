import React from "react";
import {connect} from "react-redux";
import PropTypes from "prop-types";
import {Link} from "react-router";
import {Grid, Header, Icon, Menu} from "semantic-ui-react";
import {actions, selectors, SessionWidget} from "../session";
import Modal from "../shared/Modal";
import KillProcessPopup from "../process/KillProcessPopup";
import DeleteSecretPopup from "../user/secret/DeleteSecretPopup";
import "./styles.css";

// register our modals
const MODAL_TYPES = {};
MODAL_TYPES[KillProcessPopup.MODAL_TYPE] = KillProcessPopup;
MODAL_TYPES[DeleteSecretPopup.MODAL_TYPE] = DeleteSecretPopup;

const layout = ({fullScreen, user: {displayName, loggedIn}, title, children, doLogout, router}) => {
    if (fullScreen) {
        return <Grid className="maxHeight tight">
            <Grid.Column id="mainContent" width={16} className="mainContent">
                <Modal types={MODAL_TYPES}/>
                {children}
            </Grid.Column>
        </Grid>;
    }

    return <Grid className="maxHeight tight">
        <Grid.Column width={2} className="maxHeight tight">
            <Menu size="large" vertical inverted fluid className="mainMenu maxHeight">
                <Menu.Item>
                    <Header id="logo" as="h2" inverted>{title}</Header>
                </Menu.Item>
                {loggedIn && <SessionWidget displayName={displayName} onLogout={doLogout}/>}
                <Menu.Item active={router.isActive("/process")}>
                    <Menu.Header><Icon name="tasks"/>Processes</Menu.Header>
                    <Menu.Menu>
                        <Menu.Item active={router.isActive("/process/history")}>
                            <Link to="/process/history">History</Link>
                        </Menu.Item>
                    </Menu.Menu>
                </Menu.Item>
                <Menu.Item active={router.isActive("/user")}>
                    <Menu.Header><Icon name="users"/>Users</Menu.Header>
                    <Menu.Menu>
                        <Menu.Item active={router.isActive("/user/secret")}>
                            <Link to="/user/secret">Secrets</Link>
                        </Menu.Item>
                    </Menu.Menu>
                </Menu.Item>
            </Menu>
        </Grid.Column>
        <Grid.Column id="mainContent" width={14} className="mainContent">
            <Modal types={MODAL_TYPES}/>
            {children}
        </Grid.Column>
    </Grid>;
};

layout.propTypes = {
    fullScreen: PropTypes.bool,
    user: PropTypes.object.isRequired,
    title: PropTypes.string.isRequired
};

layout.defaultProps = {
    title: "Concord"
};

const mapStateToProps = (state, {location: {query}}) => ({
    fullScreen: query.fullScreen === "true",
    user: {
        displayName: selectors.getDisplayName(state.session),
        loggedIn: selectors.isLoggedIn(state.session)
    }
});

const mapDispatchToProps = (dispatch) => ({
    doLogout: () => dispatch(actions.logOut())
});

export default connect(mapStateToProps, mapDispatchToProps)(layout);
