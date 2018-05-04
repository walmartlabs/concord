import * as React from 'react';
import { Dropdown, Icon } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import {
    DeleteRepositoryPopup,
    RefreshRepositoryPopup,
    StartRepositoryPopup
} from '../../organisms';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

class RepositoryActionDropdown extends React.PureComponent<ExternalProps> {
    render() {
        const { orgName, projectName, repoName } = this.props;

        return (
            <Dropdown icon="ellipsis vertical">
                <Dropdown.Menu>
                    <StartRepositoryPopup
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                        trigger={(onClick) => (
                            <Dropdown.Item onClick={onClick}>
                                <Icon name="play" color="blue" />
                                <span className="text">Run</span>
                            </Dropdown.Item>
                        )}
                    />

                    <RefreshRepositoryPopup
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                        trigger={(onClick) => (
                            <Dropdown.Item onClick={onClick}>
                                <Icon name="refresh" />
                                <span className="text">Refresh</span>
                            </Dropdown.Item>
                        )}
                    />

                    <DeleteRepositoryPopup
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                        trigger={(onClick) => (
                            <Dropdown.Item onClick={onClick}>
                                <Icon name="delete" color="red" />
                                <span className="text">Delete</span>
                            </Dropdown.Item>
                        )}
                    />
                </Dropdown.Menu>
            </Dropdown>
        );
    }
}

export default RepositoryActionDropdown;
