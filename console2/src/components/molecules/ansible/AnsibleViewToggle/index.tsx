/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { default as React } from 'react';
import { CheckboxProps, Popup, Radio } from 'semantic-ui-react';

export interface Props {
    checked: boolean;
    onChange: (event: React.FormEvent<HTMLInputElement>, data: CheckboxProps) => void;
}

export default (props: Props) => {
    return (
        <Popup
            trigger={<Radio label="New view (beta)" toggle={true} {...props} />}
            content="Requires Ansible plugin 1.36.0 or higher"
        />
    );
};
