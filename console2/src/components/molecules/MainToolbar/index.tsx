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
import * as React from 'react';
import { Icon, Menu, MenuItem, Sticky } from 'semantic-ui-react';
import { memo, useCallback, useState } from 'react';

import './styles.css';

interface ExternalProps {
    stickyRef: any;
    loading?: boolean;
    refresh?: () => void;
    breadcrumbs: React.ReactNode;
}

const MainToolbar = memo((props: ExternalProps) => {
    const { stickyRef, loading, refresh, breadcrumbs } = props;

    const [isFixed, setFixed] = useState(false);

    const onStick = useCallback(() => {
        setFixed(false);
    }, []);

    const onUnstick = useCallback(() => {
        setFixed(true);
    }, []);

    return (
        <Sticky context={stickyRef} onStick={onStick} onUnstick={onUnstick}>
            <Menu
                tabular={false}
                secondary={true}
                borderless={true}
                className={isFixed ? 'mainToolbar' : 'mainToolbar unfixed'}>
                {loading !== undefined && refresh !== undefined && (
                    <MenuItem>
                        <Icon name="refresh" loading={loading} size={'large'} onClick={refresh} />
                    </MenuItem>
                )}

                <MenuItem>{breadcrumbs}</MenuItem>
            </Menu>
        </Sticky>
    );
});

export default MainToolbar;
