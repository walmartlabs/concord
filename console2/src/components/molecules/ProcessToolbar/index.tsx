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
import { Button, Dropdown, Header, Icon, Menu, Sticky } from 'semantic-ui-react';

import {
    canBeCancelled,
    hasState,
    isFinal,
    ProcessEntry,
    ProcessMeta,
    ProcessStatus
} from '../../../api/process';
import { ProcessLastErrorModal } from '../index';
import { CancelProcessPopup, DisableProcessPopup } from '../../organisms';
import { memo, useCallback, useState } from 'react';
import { ConcordId } from '../../../api/common';

interface ExternalProps {
    stickyRef: any;
    loading: boolean;
    process: ProcessEntry;
    refresh: () => void;
    additionalActions?: React.ReactNode;
}

const disableIcon = (disable: boolean) => {
    return <Icon name="power" color={disable ? 'green' : 'grey'} />;
};

interface ProcessStatusToolbarProps {
    processStatus: ProcessStatus;
    processMeta?: ProcessMeta;
    loading: boolean;
    refresh: () => void;
}

const ProcessStatusToolbar = memo((props: ProcessStatusToolbarProps) => {
    const { loading, refresh, processStatus, processMeta } = props;

    return (
        <Header as="h3">
            <Icon disabled={loading} name="refresh" loading={loading} onClick={refresh} />
            <Header.Content>
                {processStatus}
                {processStatus === ProcessStatus.FAILED && (
                    <ProcessLastErrorModal processMeta={processMeta} />
                )}
            </Header.Content>
        </Header>
    );
});

interface ProcessSecondaryActionsProps {
    instanceId: ConcordId;
    processStatus: ProcessStatus;
    isProcessDisabled: boolean;
    refresh: () => void;
}

const ProcessSecondaryActions = memo((props: ProcessSecondaryActionsProps) => {
    const { instanceId, processStatus, isProcessDisabled, refresh } = props;

    const renderDisableProcessTrigger = useCallback(
        (onClick: () => void) => {
            return (
                <Dropdown.Item onClick={onClick}>
                    {disableIcon(isProcessDisabled)}
                    <span className="text">{isProcessDisabled ? 'Enable' : 'Disable'}</span>
                </Dropdown.Item>
            );
        },
        [isProcessDisabled]
    );

    return (
        <Dropdown icon="ellipsis vertical" pointing={'top right'} error={false}>
            <Dropdown.Menu>
                {isFinal(processStatus) && (
                    <DisableProcessPopup
                        instanceId={instanceId}
                        disabled={!isProcessDisabled}
                        refresh={refresh}
                        trigger={renderDisableProcessTrigger}
                    />
                )}

                {hasState(processStatus) && (
                    <Dropdown.Item
                        href={`/api/v1/process/${instanceId}/state/snapshot`}
                        download={`Concord_${processStatus}_${instanceId}.zip`}>
                        <Icon name="download" color={'blue'} />
                        <span className="text">State</span>
                    </Dropdown.Item>
                )}
            </Dropdown.Menu>
        </Dropdown>
    );
});

interface ProcessMainActionsProps {
    instanceId: ConcordId;
    processStatus: ProcessStatus;
    refresh: () => void;
}

const ProcessMainActions = memo((props: ProcessMainActionsProps) => {
    const { instanceId, processStatus, refresh } = props;

    const renderCancelProcessTrigger = useCallback(
        (onClick: () => void) => {
            return (
                <Button
                    attached={false}
                    negative={true}
                    icon="delete"
                    content="Cancel"
                    disabled={!canBeCancelled(processStatus)}
                    onClick={onClick}
                />
            );
        },
        [processStatus]
    );

    return (
        <Button.Group>
            <CancelProcessPopup
                instanceId={instanceId}
                refresh={refresh}
                trigger={renderCancelProcessTrigger}
            />
        </Button.Group>
    );
});

const ProcessToolbar = memo((props: ExternalProps) => {
    const [refreshStuck, setRefreshStuck] = useState(false);

    const onStick = useCallback(() => {
        setRefreshStuck(true);
    }, []);

    const onUnstick = useCallback(() => {
        setRefreshStuck(false);
    }, []);

    const { loading, refresh, stickyRef, process, additionalActions } = props;

    return (
        <Sticky context={stickyRef} onStick={onStick} onUnstick={onUnstick}>
            <Menu borderless={true} secondary={!refreshStuck}>
                <Menu.Item>
                    <ProcessStatusToolbar
                        loading={loading}
                        refresh={refresh}
                        processStatus={process.status}
                    />
                </Menu.Item>

                {additionalActions && <Menu.Item position={'right'}>{additionalActions}</Menu.Item>}

                <Menu.Item position={additionalActions ? undefined : 'right'}>
                    <ProcessMainActions
                        instanceId={process.instanceId}
                        processStatus={process.status}
                        refresh={refresh}
                    />
                </Menu.Item>

                <Menu.Item>
                    <ProcessSecondaryActions
                        instanceId={process.instanceId}
                        processStatus={process.status}
                        isProcessDisabled={process.disabled}
                        refresh={refresh}
                    />
                </Menu.Item>
            </Menu>
        </Sticky>
    );
});

export default ProcessToolbar;
