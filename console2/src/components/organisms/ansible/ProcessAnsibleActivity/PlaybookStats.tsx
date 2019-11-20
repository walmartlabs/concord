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
import { Card, SemanticCOLORS, Statistic } from 'semantic-ui-react';

import { useCallback } from 'react';

export enum Blocks {
    'hosts',
    'failedHosts',
    'plays',
    'failedTasks'
}

export interface PlaybookStatsProps {
    hostsCount: number;
    failedHostsCount: number;
    playsCount: number;
    failedTasksCount: number;
    selectedBlock?: Blocks;
    onBlockChange: (block: Blocks) => void;
}

const activeBlockColor: SemanticCOLORS = 'green';

const PlaybookStats = ({
    hostsCount,
    failedHostsCount,
    playsCount,
    failedTasksCount,
    selectedBlock,
    onBlockChange
}: PlaybookStatsProps) => {
    const onBlockToggle = useCallback(
        (block: Blocks) => {
            onBlockChange(block);
        },
        [onBlockChange]
    );

    return (
        <>
            <Card.Group itemsPerRow={4}>
                <Card
                    onClick={hostsCount > 0 ? () => onBlockToggle(Blocks.hosts) : undefined}
                    color={selectedBlock === Blocks.hosts ? activeBlockColor : undefined}
                    className={selectedBlock === Blocks.hosts ? 'playbookStatsBlockToggled' : ''}>
                    <Card.Content textAlign={'center'}>
                        <Statistic color="black" size={'tiny'}>
                            <Statistic.Value>{hostsCount}</Statistic.Value>
                            <Statistic.Label className={'playbookStatsLabel'}>
                                UNIQUE HOSTS
                            </Statistic.Label>
                        </Statistic>
                    </Card.Content>
                </Card>
                <Card
                    onClick={
                        failedHostsCount > 0 ? () => onBlockToggle(Blocks.failedHosts) : undefined
                    }
                    color={selectedBlock === Blocks.failedHosts ? activeBlockColor : undefined}
                    className={
                        selectedBlock === Blocks.failedHosts ? 'playbookStatsBlockToggled' : ''
                    }>
                    <Card.Content textAlign={'center'}>
                        <Statistic color={failedHostsCount > 0 ? 'red' : 'black'} size={'tiny'}>
                            <Statistic.Value>{failedHostsCount}</Statistic.Value>
                            <Statistic.Label className={'playbookStatsLabel'}>
                                FAILED HOSTS
                            </Statistic.Label>
                        </Statistic>
                    </Card.Content>
                </Card>
                <Card
                    onClick={playsCount > 0 ? () => onBlockToggle(Blocks.plays) : undefined}
                    color={selectedBlock === Blocks.plays ? activeBlockColor : undefined}
                    className={selectedBlock === Blocks.plays ? 'playbookStatsBlockToggled' : ''}>
                    <Card.Content textAlign={'center'}>
                        <Statistic color="black" size={'tiny'}>
                            <Statistic.Value>{playsCount}</Statistic.Value>
                            <Statistic.Label className={'playbookStatsLabel'}>
                                PLAYS
                            </Statistic.Label>
                        </Statistic>
                    </Card.Content>
                </Card>
                <Card
                    onClick={
                        failedTasksCount > 0 ? () => onBlockToggle(Blocks.failedTasks) : undefined
                    }
                    color={selectedBlock === Blocks.failedTasks ? activeBlockColor : undefined}
                    className={
                        selectedBlock === Blocks.failedTasks ? 'playbookStatsBlockToggled' : ''
                    }>
                    <Card.Content textAlign={'center'}>
                        <Statistic color={failedTasksCount > 0 ? 'red' : 'black'} size={'tiny'}>
                            <Statistic.Value>{failedTasksCount}</Statistic.Value>
                            <Statistic.Label className={'playbookStatsLabel'}>
                                FAILED TASKS
                            </Statistic.Label>
                        </Statistic>
                    </Card.Content>
                </Card>
            </Card.Group>
        </>
    );
};

export default PlaybookStats;
