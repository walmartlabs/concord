import * as React from 'react';
import { Menu, Modal } from 'semantic-ui-react';
import ConnectedAnsibleTaskList from '../AnsibleTaskList';

export class HostnameListItem extends React.Component {
    render() {
        const { hostname, selectedHost, setSelectedHostFn } = this.props;

        return (
            <Modal
                trigger={
                    <Menu.Item
                        name={hostname}
                        active={selectedHost === hostname}
                        onClick={() => setSelectedHostFn(hostname)}>
                        {hostname}
                    </Menu.Item>
                }
                basic
                size="fullscreen">
                <Modal.Content>
                    <ConnectedAnsibleTaskList />
                </Modal.Content>
            </Modal>
        );
    }
}

export default HostnameListItem;
