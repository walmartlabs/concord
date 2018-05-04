import * as React from 'react';
import { Link } from 'react-router-dom';
import { Dropdown, Image, Menu } from 'semantic-ui-react';

export type GlobalNavTab = 'process' | 'org' | null;

interface Props {
    activeTab: GlobalNavTab;
    userDisplayName?: string;
    logOut: () => void;
}

class GlobalNavMenu extends React.PureComponent<Props> {
    render() {
        const { activeTab, userDisplayName, logOut } = this.props;

        return (
            <Menu fluid={true} tabular={true} size="small">
                <Menu.Item>
                    <Image src="/images/concord.svg" size="small" />
                </Menu.Item>
                <Menu.Item active={activeTab === 'process'}>
                    <Link to="/process">Processes</Link>
                </Menu.Item>
                <Menu.Item active={activeTab === 'org'}>
                    <Link to="/org">Organizations</Link>
                </Menu.Item>
                <Menu.Item position="right" as={Dropdown} text={userDisplayName}>
                    {/* TODO can't add an icon here */}
                    <Dropdown.Menu>
                        <Dropdown.Item icon="log out" text="Log out" onClick={() => logOut()} />
                    </Dropdown.Menu>
                </Menu.Item>
            </Menu>
        );
    }
}

export default GlobalNavMenu;
