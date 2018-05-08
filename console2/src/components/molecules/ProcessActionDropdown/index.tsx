import * as React from 'react';
import { Dropdown, Icon } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { canBeCancelled, ProcessStatus } from '../../../api/process';
import { CancelProcessPopup } from '../../organisms';

interface ExternalProps {
    instanceId: ConcordId;
    status: ProcessStatus;
}

class RepositoryActionDropdown extends React.PureComponent<ExternalProps> {
    render() {
        const { instanceId, status } = this.props;

        return (
            <Dropdown icon="ellipsis vertical">
                <Dropdown.Menu>
                    <CancelProcessPopup
                        instanceId={instanceId}
                        trigger={(onClick) => (
                            <Dropdown.Item onClick={onClick} disabled={!canBeCancelled(status)}>
                                <Icon name="delete" color="red" />
                                <span className="text">Cancel</span>
                            </Dropdown.Item>
                        )}
                    />
                </Dropdown.Menu>
            </Dropdown>
        );
    }
}

export default RepositoryActionDropdown;
