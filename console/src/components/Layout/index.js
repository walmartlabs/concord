import React, {Component, PropTypes} from "react";
import {Menu, Header, Grid, Icon} from "semantic-ui-react";
import {Link} from "react-router";
import * as routes from "../../routes";
import "./styles.css";

class Layout extends Component {

    isPathActive(path) {
        return this.props.router.isActive(path);
    }

    render() {
        const menuItemFn = (path, label) => {
            const active = path && this.isPathActive(path);
            return <Menu.Item active={active}>
                {active ? label : (path ? <Link to={path}>{label}</Link> : label)}
            </Menu.Item>;
        };

        const {children, loggedIn} = this.props;
        if (!loggedIn) {
            return <Grid className="maxHeight tight">
                <Grid.Column width={16} className="maxHeight tight">
                    {children}
                </Grid.Column>
            </Grid>;
        }

        return (
            <Grid className="maxHeight tight">
                <Grid.Column width={2} className="maxHeight tight">
                    <Menu size="large" vertical inverted fluid className="mainMenu maxHeight">
                        <Menu.Item>
                            <Header id="logo" as="h2" inverted>Concord</Header>
                        </Menu.Item>

                        <Menu.Item active={this.isPathActive("/project")}>
                            <Icon name="lab"/>Projects
                            <Menu.Menu>
                                {menuItemFn(routes.getProjectListPath(), "List")}
                                {menuItemFn(routes.getProjectNewPath(), "Create a project")}
                                {menuItemFn(null, "Templates")}
                            </Menu.Menu>
                        </Menu.Item>

                        <Menu.Item active={this.isPathActive("/process")}>
                            <Icon name="tasks"/>Processes
                            <Menu.Menu>
                                {menuItemFn(routes.getProcessHistoryPath(), "History")}
                                {menuItemFn(null, "Start a process")}
                            </Menu.Menu>
                        </Menu.Item>

                        <Menu.Item>
                            <Icon name="users"/>Users
                        </Menu.Item>

                        <Menu.Item>
                            <Icon name="privacy"/>Credentials
                        </Menu.Item>
                    </Menu>
                </Grid.Column>
                <Grid.Column id="mainContent" width={14} className="mainContent">{children}</Grid.Column>
            </Grid>
        );
    }
}

Layout.propTypes = {
    router: PropTypes.any.isRequired,
    loggedIn: PropTypes.bool
};

export default Layout;
