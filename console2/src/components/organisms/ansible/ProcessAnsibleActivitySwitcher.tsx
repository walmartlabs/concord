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

import { ProcessAnsibleActivity, ProcessAnsibleActivityOld } from '../../organisms';
import { useCallback } from 'react';
import { useState } from 'react';
import { ConcordId } from '../../../api/common';
import { AnsibleViewToggle, ProcessToolbar } from '../../molecules';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
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

    const switchHandler = useCallback((ev: any, { checked }: any) => {
        const oldLook = !checked;
        setOldLook(oldLook);
        storeViewMode(oldLook ? 'OLD' : 'NEW');
    }, []);

    return (
        <>
            <ProcessToolbar>
                <AnsibleViewToggle checked={!oldLook} onChange={switchHandler} />
            </ProcessToolbar>

            {oldLook && (
                <ProcessAnsibleActivityOld
                    instanceId={props.instanceId}
                    loadingHandler={props.loadingHandler}
                    forceRefresh={props.forceRefresh}
                />
            )}

            {!oldLook && (
                <ProcessAnsibleActivity
                    instanceId={props.instanceId}
                    loadingHandler={props.loadingHandler}
                    forceRefresh={props.forceRefresh}
                />
            )}
        </>
    );
};

export default ProcessAnsibleActivitySwitcher;
