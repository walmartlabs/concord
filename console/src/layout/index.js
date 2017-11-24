import React from "react";
import {connect} from "react-redux";
import PropTypes from "prop-types";
import {Link} from "react-router";
import {Grid, Header, Icon, Menu, Segment, Loader} from "semantic-ui-react";
import {actions, selectors, SessionWidget} from "../session";
import Modal from "../shared/Modal";
import KillProcessPopup from "../process/KillProcessPopup";
import DeleteSecretPopup from "../team/secret/list/DeleteSecretPopup";
import ShowSecretPublicKey from "../team/secret/list/ShowSecretPublicKey";
import RepositoryPopup from "../project/RepositoryPopup";
import DeleteProjectPopup from "../project/DeleteProjectPopup";
import StartProjectPopup from "../project/StartProjectPopup/StartProjectPopup";
import "./styles.css";

// register our modals
const MODAL_TYPES = {};
MODAL_TYPES[KillProcessPopup.MODAL_TYPE] = KillProcessPopup;
MODAL_TYPES[DeleteSecretPopup.MODAL_TYPE] = DeleteSecretPopup;
MODAL_TYPES[RepositoryPopup.MODAL_TYPE] = RepositoryPopup;
MODAL_TYPES[DeleteProjectPopup.MODAL_TYPE] = DeleteProjectPopup;
MODAL_TYPES[StartProjectPopup.MODAL_TYPE] = StartProjectPopup;
MODAL_TYPES[ShowSecretPublicKey.MODAL_TYPE] = ShowSecretPublicKey;


const layout = ({fullScreen, user: {displayName, teamName, loggedIn}, title, children, doLogout, router}) => {
    if (fullScreen) {
        return <Grid className="maxHeight tight">
            <Grid.Column id="mainContent" width={16} className="mainContent">
                <Modal types={MODAL_TYPES}/>
                {children}
            </Grid.Column>
        </Grid>;
    }

    if (!loggedIn || !teamName) {
        return <Segment className="maxHeight">
            <Loader/>
        </Segment>;
    }

    return <Grid className="maxHeight tight">
        <Grid.Column width={2} className="maxHeight tight">
            <Menu size="large" vertical inverted fluid className="mainMenu maxHeight">
                <Menu.Item>
                    <Header id="logo" as="h2" inverted>{title}</Header>
                </Menu.Item>
                <SessionWidget displayName={displayName} teamName={teamName} onLogout={doLogout}/>
                <Menu.Item active={router.isActive("/process")}>
                    <Menu.Header><Icon name="tasks"/>Processes</Menu.Header>
                    <Menu.Menu>
                        <Menu.Item active={router.isActive("/process/queue")}>
                            <Link to="/process/queue">Queue</Link>
                        </Menu.Item>
                    </Menu.Menu>
                </Menu.Item>
                <Menu.Item active={router.isActive("/project")}>
                    <Menu.Header><Icon name="book"/>Projects</Menu.Header>
                    <Menu.Menu>
                        <Menu.Item active={router.isActive("/project/list")}>
                            <Link to="/project/list">List</Link>
                        </Menu.Item>
                        <Menu.Item active={router.isActive("/project/_new")}>
                            <Link to="/project/_new">Create new</Link>
                        </Menu.Item>
                    </Menu.Menu>
                </Menu.Item>
                <Menu.Item active={router.isActive("/secret")}>
                    <Menu.Header><Icon name="lock"/>Secrets</Menu.Header>
                    <Menu.Menu>
                        <Menu.Item active={router.isActive("/secret/list")}>
                            <Link to="/secret/list">List</Link>
                        </Menu.Item>
                        <Menu.Item active={router.isActive("/secret/_new")}>
                            <Link to="/secret/_new">Create New</Link>
                        </Menu.Item>
                    </Menu.Menu>
                </Menu.Item>
                <Menu.Item active={router.isActive("/system")}>
                    <Menu.Header><Icon name="lab"/>System</Menu.Header>
                    <Menu.Menu>
                        <Menu.Item active={router.isActive("/system/about")}>
                            <Link to="/system/about">About</Link>
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
        loggedIn: selectors.isLoggedIn(state.session),
        teamName: selectors.getCurrentTeamName(state.session)
    }
});

const mapDispatchToProps = (dispatch) => ({
    doLogout: () => dispatch(actions.logOut())
});

export default connect(mapStateToProps, mapDispatchToProps)(layout);
