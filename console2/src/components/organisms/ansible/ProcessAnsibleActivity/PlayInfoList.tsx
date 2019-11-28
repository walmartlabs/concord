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
import { Label, Popup, Progress, SemanticCOLORS, Table } from 'semantic-ui-react';

import {
    FAILED_COLOR,
    OK_COLOR,
    PlayInfoEntry,
    RUNNING_COLOR,
    SKIPPED_COLOR,
    UNREACHABLE_COLOR
} from './types';
import { ConcordId } from '../../../../api/common';

export interface PlayStatsProps {
    stats?: PlayInfoEntry[];
    playbookStatus: string;
    selectedPlayId?: ConcordId;
    playClickAction: (id: ConcordId) => void;
}

const renderTaskCount = (value: number, label: string, color?: SemanticCOLORS) => {
    return (
        <Popup
            content={label}
            mouseEnterDelay={500}
            trigger={
                <Label
                    color={color}
                    horizontal
                    className={value > 0 ? 'taskCounterActive' : 'taskCounterNonactive'}>
                    {value}
                </Label>
            }
        />
    );
};

const renderPlayRow = (
    s: PlayInfoEntry,
    isPlaybookRunning: boolean,
    index: number,
    playClickAction: (id: ConcordId) => void,
    selectedPlayId?: ConcordId
) => {
    return (
        <Table.Row
            key={index}
            onClick={() => playClickAction(s.id)}
            style={{ cursor: 'pointer' }}
            className={selectedPlayId === s.id ? 'selectedPlay' : ''}>
            <Table.Cell singleLine={true}>{s.play}</Table.Cell>
            <Table.Cell singleLine={true} textAlign={'center'} collapsing={true}>
                {s.hostsCount}
            </Table.Cell>
            <Table.Cell width={1} textAlign={'center'} collapsing={true} singleLine={true}>
                {s.tasksCount}
            </Table.Cell>

            <Table.Cell singleLine={true} collapsing={true}>
                {renderTaskCount(s.taskStats.ok, 'OK', OK_COLOR)}
                {renderTaskCount(s.taskStats.failed, 'FAILED', FAILED_COLOR)}
                {renderTaskCount(s.taskStats.unreachable, 'UNREACHABLE', UNREACHABLE_COLOR)}
                {renderTaskCount(s.taskStats.skipped, 'SKIPPED', SKIPPED_COLOR)}
                {renderTaskCount(s.taskStats.running, 'RUNNING', RUNNING_COLOR)}
            </Table.Cell>

            <Table.Cell singleLine={true} width={2}>
                <Progress
                    percent={s.progress}
                    active={isPlaybookRunning && s.progress > 0 && s.progress < 100}
                    progress={'percent'}
                    className={'playProgress ' + (s.progress > 0 ? '' : 'nonactive')}
                />
            </Table.Cell>
        </Table.Row>
    );
};

const renderElements = ({
    stats,
    playbookStatus,
    playClickAction,
    selectedPlayId
}: PlayStatsProps) => {
    if (!stats) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={4}>-</Table.Cell>
            </tr>
        );
    }

    if (stats.length === 0) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={4}>No data available</Table.Cell>
            </tr>
        );
    }

    return stats.map((value, index) =>
        renderPlayRow(value, playbookStatus === 'RUNNING', index, playClickAction, selectedPlayId)
    );
};

const PlayInfoList = (props: PlayStatsProps) => {
    return (
        <Table
            compact={true}
            basic={true}
            selectable={true}
            className={props.stats ? '' : 'loading'}>
            <Table.Header>
                <Table.Row>
                    <Table.HeaderCell>Play</Table.HeaderCell>
                    <Table.HeaderCell>Hosts</Table.HeaderCell>
                    <Table.HeaderCell singleLine={true} collapsing={true}>
                        Unique Tasks
                    </Table.HeaderCell>
                    <Table.HeaderCell colSpan={2} collapsing={true}>
                        Progress (tasks * hosts)
                    </Table.HeaderCell>
                </Table.Row>
            </Table.Header>
            <Table.Body>{renderElements(props)}</Table.Body>
        </Table>
    );
};

export default PlayInfoList;
