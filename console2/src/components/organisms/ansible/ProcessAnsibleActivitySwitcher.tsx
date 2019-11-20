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

import { ProcessEntry } from '../../../api/process';
import ProcessAnsibleActivityOld from './ProcessAnsibleActivityOld';
import ProcessAnsibleActivity from './ProcessAnsibleActivity';
import { useCallback } from 'react';
import { useState } from 'react';

interface ExternalProps {
    process: ProcessEntry;
}

const getViewMode = (): string => {
    const o = localStorage.getItem('ansibleViewMode');
    if (!o) {
        return 'OLD';
    }
    return o;
};

const storeViewMode = (mode: string) => {
    localStorage.setItem('ansibleViewMode', mode);
};

const ProcessAnsibleActivitySwitcher = (props: ExternalProps) => {
    const [oldLook, setOldLook] = useState<Boolean>(getViewMode() === 'OLD');

    const viewSwitchHandler = useCallback((checked: boolean) => {
        const oldLook = !checked;
        setOldLook(oldLook);
        storeViewMode(oldLook ? 'OLD' : 'NEW');
    }, []);

    if (oldLook) {
        return (
            <ProcessAnsibleActivityOld
                process={props.process}
                viewSwitchHandler={viewSwitchHandler}
            />
        );
    } else {
        return (
            <ProcessAnsibleActivity process={props.process} viewSwitchHandler={viewSwitchHandler} />
        );
    }
};

export default ProcessAnsibleActivitySwitcher;
