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
import { Menu } from 'semantic-ui-react';

import './styles.css';

interface ExternalProps {
    children: React.ReactNode;
}

const ProcessToolbar = ({ children }: ExternalProps) => {
    return (
        <Menu borderless={true} secondary={true} className={'processToolbar'}>
            <Menu.Item style={{width: '100%'}}>{children}</Menu.Item>
        </Menu>
    );
};

export default ProcessToolbar;
