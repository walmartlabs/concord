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
import { BulkCancelProcessPopup } from '../../organisms';

interface ExternalProps {
    data: ConcordId[];
    refresh: () => void;
}

class BulkProcessActionDropdown extends React.PureComponent<ExternalProps> {
    render() {
        const { data, refresh } = this.props;
        return (
            <Dropdown text="Actions" disabled={data.length === 0} button={true}>
                <Dropdown.Menu>
                    <BulkCancelProcessPopup
                        data={data}
                        refresh={refresh}
                        trigger={(onClick) => (
                            <Dropdown.Item onClick={onClick}>
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

export default BulkProcessActionDropdown;
