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
import { memo, useCallback, useState } from 'react';
import {
    canBeCancelled,
    canBeRestarted,
    hasState,
    isFinal,
    ProcessEntry,
    ProcessStatus
} from '../../../api/process';
import {
    Breadcrumb,
    Button,
    Dropdown,
    Icon,
    Label,
    Menu,
    MenuItem,
    Sticky
} from 'semantic-ui-react';
import { Link } from 'react-router-dom';
import { formatDistanceToNow, isAfter, parseISO as parseDate } from 'date-fns';
import { SemanticCOLORS } from 'semantic-ui-react/dist/commonjs/generic';

import { ProcessLastErrorModal, WithCopyToClipboard } from '../../molecules';
import { CancelProcessPopup, DisableProcessPopup } from '../../organisms';
import { ConcordId } from '../../../api/common';

import './styles.css';
import { formatDuration } from '../../../utils';
import RestartProcessPopup from "../RestartProcessPopup";

interface ExternalProps {
    stickyRef: any;
    loading: boolean;
    instanceId: ConcordId;
    process?: ProcessEntry;
    rootInstanceId?: ConcordId;
    refresh: () => void;
}

const ProcessToolbar = memo((props: ExternalProps) => {
    const { stickyRef, loading, refresh, process, instanceId, rootInstanceId } = props;

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
                className={isFixed ? 'ProcessMainToolbar' : 'ProcessMainToolbar unfixed'}>
                <MenuItem>
                    <Icon name="refresh" loading={loading} size={'large'} onClick={refresh} />
                </MenuItem>

                <MenuItem>{renderBreadcrumbs(instanceId, process)}</MenuItem>

                <MenuItem>{renderProcessStatus(process)}</MenuItem>

                <MenuItem>{renderStartAt(process)}</MenuItem>

                <MenuItem position={'right'}>{renderProcessMainActions(refresh, process, rootInstanceId)}</MenuItem>

                <MenuItem>{renderProcessSecondaryActions(refresh, process)}</MenuItem>
            </Menu>
        </Sticky>
    );
});

const renderBreadcrumbs = (instanceId: ConcordId, process?: ProcessEntry) => {
    if (!process) {
        return (
            <Breadcrumb size="big">
                <Breadcrumb.Section active={true}>{instanceId}</Breadcrumb.Section>
            </Breadcrumb>
        );
    }

    if (!process.orgName) {
        return (
            <Breadcrumb size="big">
                <Breadcrumb.Section>
                    <Link to={`/process`}>Processes</Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section active={true}>
                    <WithCopyToClipboard value={process.instanceId}>
                        {process.instanceId}
                    </WithCopyToClipboard>
                </Breadcrumb.Section>
            </Breadcrumb>
        );
    }

    return (
        <Breadcrumb size="big">
            <Breadcrumb.Section>
                <Link to={`/org/${process.orgName}`}>{process.orgName}</Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section>
                <Link to={`/org/${process.orgName}/project/${process.projectName}`}>
                    {process.projectName}
                </Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section active={true}>
                <WithCopyToClipboard value={process.instanceId}>
                    {process.instanceId}
                </WithCopyToClipboard>
            </Breadcrumb.Section>
        </Breadcrumb>
    );
};

const renderProcessStatus = (process?: ProcessEntry) => {
    if (!process) {
        return;
    }

    let duration;
    if (process.status === ProcessStatus.RUNNING) {
        duration = formatDuration(new Date().getTime() - parseDate(process.lastRunAt || process.createdAt).getTime());
    }
    return (
        <>
            <Label color={getStatusColor(process.status)}>
                {process.status}
                {duration && <Label.Detail>{duration}</Label.Detail>}
            </Label>
            {process.status === ProcessStatus.FAILED && (
                <>
                    &nbsp;
                    <ProcessLastErrorModal processMeta={process.meta} />
                </>
            )}
        </>
    );
};

const renderStartAt = (process?: ProcessEntry) => {
    if (!process || !process.startAt || process.status !== ProcessStatus.ENQUEUED) {
        return;
    }

    let startAt = parseDate(process.startAt);
    if (isAfter(startAt, Date.now())) {
        return <span className="startAt">starts in {formatDistanceToNow(startAt)}</span>;
    }

    return;
};

const renderProcessMainActions = (refresh: () => void, process?: ProcessEntry, rootInstanceId?: ConcordId) => {
    if (!process) {
        return (
            <Button.Group>
                <Button
                    attached={false}
                    negative={false}
                    icon="delete"
                    content="Cancel"
                    disabled={true}
                    size={'small'}
                />
            </Button.Group>
        );
    }

    const renderRestartProcessTrigger = (onClick: () => void) => {
        return (
            <Button
                attached={false}
                negative={false}
                icon="sync"
                content="Restart"
                disabled={!canBeRestarted(process.status)}
                size={'small'}
                onClick={onClick}
            />
        );
    };

    const renderCancelProcessTrigger = (onClick: () => void) => {
        return (
            <Button
                attached={false}
                negative={true}
                icon="delete"
                content="Cancel"
                disabled={!canBeCancelled(process.status)}
                size={'small'}
                onClick={onClick}
            />
        );
    };

    return (
        <Button.Group>
            {!canBeRestarted(process.status) &&
                <CancelProcessPopup
                    instanceId={process.instanceId}
                    refresh={refresh}
                    trigger={renderCancelProcessTrigger}
                />
            }
            {canBeRestarted(process.status) && process.runtime === 'concord-v2' &&
                <RestartProcessPopup
                    instanceId={process.instanceId}
                    rootInstanceId={rootInstanceId}
                    refresh={refresh}
                    trigger={renderRestartProcessTrigger}
                />
            }
        </Button.Group>
    );
};

const renderProcessSecondaryActions = (refresh: () => void, process?: ProcessEntry) => {
    if (!process) {
        return (
            <Dropdown
                icon="ellipsis vertical"
                pointing={'top right'}
                error={false}
                disabled={true}
            />
        );
    }

    const { instanceId, status, disabled } = process;

    const renderDisableProcessTrigger = (onClick: () => void) => {
        return (
            <Dropdown.Item onClick={onClick}>
                {disableIcon(disabled)}
                <span className="text">{disabled ? 'Enable' : 'Disable'}</span>
            </Dropdown.Item>
        );
    };

    const extraProcessMenuLinks = window.concord?.extraProcessMenuLinks;

    const getIcon = ({ props }: { props: any }) => <Icon color={props.color} name={props.icon} />;

    return (
        <Dropdown icon="ellipsis vertical" pointing={'top right'} error={false}>
            <Dropdown.Menu>
                {isFinal(status) && (
                    <DisableProcessPopup
                        instanceId={instanceId}
                        disabled={!disabled}
                        refresh={refresh}
                        trigger={renderDisableProcessTrigger}
                    />
                )}

                {hasState(status) && (
                    <Dropdown.Item
                        href={`/api/v1/process/${instanceId}/state/snapshot`}
                        download={`Concord_${status}_${instanceId}.zip`}>
                        <Icon name="download" color={'blue'} />
                        <span className="text">State</span>
                    </Dropdown.Item>
                )}

                <Dropdown.Item
                    href={`/api/v1/process/${process.instanceId}/state/snapshot/.concord/effective.concord.yml`}
                    download={`${instanceId}.concord.yml`}>
                    <Icon name="download" color={'green'} />
                    <span className="text">Effective YAML</span>
                </Dropdown.Item>

                {extraProcessMenuLinks &&
                    extraProcessMenuLinks.map((x, idx) => (
                        <Dropdown.Item
                            key={idx}
                            text={x.label}
                            onClick={() =>
                                window.open(`${x.url}?arguments.instanceId=${instanceId}`, '_blank')
                            }>
                            {getIcon({ props: x })}
                            <span className="text">{x.label}</span>
                        </Dropdown.Item>
                    ))}
            </Dropdown.Menu>
        </Dropdown>
    );
};

const disableIcon = (disable: boolean) => {
    return <Icon name="power" color={disable ? 'green' : 'grey'} />;
};

const getStatusColor = (status: string): SemanticCOLORS => {
    switch (status) {
        case ProcessStatus.FINISHED:
            return 'green';
        case ProcessStatus.FAILED:
            return 'red';
        default:
            return 'grey';
    }
};

export default ProcessToolbar;
