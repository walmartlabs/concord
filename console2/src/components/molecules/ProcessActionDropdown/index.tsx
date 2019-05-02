/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import * as React from 'react';
import { Dropdown, Icon } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { canBeCancelled, ProcessStatus } from '../../../api/process';
import { CancelProcessPopup } from '../../organisms';

interface ExternalProps {
    instanceId: ConcordId;
    status: ProcessStatus;
    refresh: () => void;
}

class ProcessActionDropdown extends React.PureComponent<ExternalProps> {
    render() {
        const { instanceId, status, refresh } = this.props;

        return (
            <Dropdown icon="ellipsis vertical">
                <Dropdown.Menu>
                    <CancelProcessPopup
                        instanceId={instanceId}
                        refresh={refresh}
                        trigger={(onClick: any) => (
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

export default ProcessActionDropdown;
