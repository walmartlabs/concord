import React, {Component, PropTypes} from "react";
import {Grid, Header, Icon, Menu} from "semantic-ui-react";
import {Link} from "react-router";
import * as routes from "../../routes";
import "./styles.css";

class Layout extends Component {

    isPathActive(path) {
        return this.props.router.isActive(path);
    }

    render() {
        const {title, children, fullScreen} = this.props;

        if (fullScreen) {
            return <Grid className="maxHeight tight">
                <Grid.Column width={16} className="mainContent">
                    <Menu size="large" inverted>
                        <Menu.Item>
                            <Header id="logo" as="h2" inverted>{title}</Header>
                        </Menu.Item>
                    </Menu>
                    {children}
                </Grid.Column>
            </Grid>;
        }

        const menuItemFn = (path, label) => {
            const active = path && this.isPathActive(path);
            return <Menu.Item active={active}>
                {active ? label : (path ? <Link to={path}>{label}</Link> : label)}
            </Menu.Item>;
        };

        return (
            <Grid className="maxHeight tight">
                <Grid.Column width={2} className="maxHeight tight">
                    <Menu size="large" vertical inverted fluid className="mainMenu maxHeight">
                        <Menu.Item>
                            <Header id="logo" as="h2" inverted>{title}</Header>
                        </Menu.Item>

                        {/*
                         <Menu.Item active={this.isPathActive("/project")}>
                         <Icon name="lab"/>Projects
                         <Menu.Menu>
                         {menuItemFn(routes.getProjectListPath(), "List")}
                         {menuItemFn(routes.getProjectNewPath(), "Create a project")}
                         {menuItemFn(null, "Templates")}
                         </Menu.Menu>
                         </Menu.Item>
                         */}

                        <Menu.Item active={this.isPathActive("/process")}>
                            <Icon name="tasks"/>Processes
                            <Menu.Menu>
                                {menuItemFn(routes.getProcessHistoryPath(), "History")}
                                {/*{menuItemFn(null, "Start a process")}*/}
                            </Menu.Menu>
                        </Menu.Item>

                        {/*
                         <Menu.Item>
                         <Icon name="users"/>Users
                         </Menu.Item>

                         <Menu.Item>
                         <Icon name="privacy"/>Credentials
                         </Menu.Item>
                         */}
                    </Menu>
                </Grid.Column>
                <Grid.Column id="mainContent" width={14} className="mainContent">{children}</Grid.Column>
            </Grid>
        );
    }
}

Layout.propTypes = {
    title: PropTypes.string.isRequired,
    router: PropTypes.any.isRequired,
    fullScreen: PropTypes.bool
};

export default Layout;
