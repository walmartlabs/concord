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
import { Dropdown, DropdownItemProps, DropdownProps } from 'semantic-ui-react';

import { ResourceAccessLevel } from '../../../api/org';

interface Props extends DropdownProps {
    onRoleChange: (value: ResourceAccessLevel) => void;
}

const options: DropdownItemProps[] = [
    { text: 'Writer', value: ResourceAccessLevel.WRITER },
    { text: 'Reader', value: ResourceAccessLevel.READER },
    { text: 'Owner', value: ResourceAccessLevel.OWNER }
];

class TeamAccessDropdown extends React.PureComponent<Props> {
    render() {
        const { onRoleChange, ...rest } = this.props;
        return (
            <Dropdown
                selection={true}
                options={options}
                onChange={(ev, data) => onRoleChange(ResourceAccessLevel[data.value as string])}
                {...rest}
            />
        );
    }
}

export default TeamAccessDropdown;
