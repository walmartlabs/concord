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
import { Dropdown, DropdownItemProps } from 'semantic-ui-react';
import { DropdownProps } from 'semantic-ui-react/dist/commonjs/modules/Dropdown/Dropdown';
import { ProcessStatus } from '../../../api/process';

const options: DropdownItemProps[] = [
    { key: '', value: undefined, text: 'any' },
    ...Object.keys(ProcessStatus).map((k) => ({
        key: k,
        value: k,
        text: k
    }))
];

class ProcessStatusDropdown extends React.PureComponent<DropdownProps> {
    render() {
        return <Dropdown {...this.props} options={options} placeholder="Status" selection={true} />;
    }
}

export default ProcessStatusDropdown;
