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
import { Block, Sidebar, Backdrop } from 'reakit';
import StyledSidebar from './Sidebar';

import { namespace } from './context';

export interface SidebarContainerProps {
    align?: 'left' | 'right';
}

const SidebarContainer: React.SFC<SidebarContainerProps> = ({ align, children }) => {
    return (
        <Sidebar.Container context={namespace}>
            {(sidebar) => (
                <Block>
                    <Backdrop fade={true} as={Sidebar.Hide as any} {...sidebar} />
                    <StyledSidebar slide={true} align={align} {...sidebar}>
                        {children}
                    </StyledSidebar>
                </Block>
            )}
        </Sidebar.Container>
    );
};

export default SidebarContainer;
