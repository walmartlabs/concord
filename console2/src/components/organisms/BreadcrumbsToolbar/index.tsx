/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { Breadcrumb, Button, Icon, Menu, MenuItem } from 'semantic-ui-react';
import './styles.css';

interface ExternalProps {
    refreshHandler: () => void;
    loading: boolean;
    children: React.ReactNode;
}

export default ({ loading, refreshHandler, children }: ExternalProps) => {
    return (
        <Menu tabular={false} secondary={true} borderless={true} className="BreadcrumbsToolbar">
            <MenuItem>
                <Icon name="refresh" loading={loading} size={'large'} onClick={refreshHandler} />
            </MenuItem>

            <MenuItem>
                <Breadcrumb size="big">{children}</Breadcrumb>
            </MenuItem>

            {/* add a hidden button to match (the toolbar's + action buttons) vertical size */}
            <MenuItem position={'right'} style={{ opacity: 0 }}>
                <Button.Group>
                    <Button
                        attached={false}
                        icon="refresh"
                        disabled={true}
                        size={'small'}
                        basic={true}
                    />
                </Button.Group>
            </MenuItem>
        </Menu>
    );
};
