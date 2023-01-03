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
import { Table } from 'semantic-ui-react';
import * as React from 'react';
import TaskProgress, { ProgressStats } from './TaskProgress';
import { TaskInfoEntry } from './types';
import { memo } from 'react';

export interface TaskStatsProps {
    totalTaskWork: number;
    tasks?: TaskInfoEntry[];
}

const renderTaskRow = (
    name: string,
    type: string,
    stats: ProgressStats,
    total: number,
    index: number
) => {
    return (
        <Table.Row key={index} style={type !== 'TASK' ? { opacity: 0.5 } : {}}>
            <Table.Cell
                collapsing={true}
                singleLine={true}
                textAlign={'right'}
                className="taskProgressCell">
                {name}
            </Table.Cell>
            <Table.Cell collapsing={true} singleLine={true} className="taskProgressCell">
                <TaskProgress total={total} stats={stats} />
            </Table.Cell>
        </Table.Row>
    );
};

const renderTableBody = (totalTaskWork: number, tasks?: TaskInfoEntry[]) => {
    if (!tasks) {
        return (
            <Table.Row style={{ fontWeight: 'bold' }}>
                <Table.Cell
                    collapsing={true}
                    singleLine={true}
                    textAlign={'right'}
                    className="taskProgressCell">
                    -
                </Table.Cell>
                <Table.Cell>&nbsp;</Table.Cell>
            </Table.Row>
        );
    }

    return tasks.map((value, index) =>
        renderTaskRow(value.name, value.type, value.stats, totalTaskWork, index)
    );
};

const TaskStats = memo(({ totalTaskWork, tasks }: TaskStatsProps) => {
    return (
        <>
            <Table compact={true} basic={'very'} columns={2} className={tasks ? '' : 'loading'}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true} singleLine={true} textAlign={'right'}>
                            Task
                        </Table.HeaderCell>
                        <Table.HeaderCell collapsing={true} singleLine={true}>
                            Host execution count
                        </Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>{renderTableBody(totalTaskWork, tasks)}</Table.Body>
            </Table>
        </>
    );
});

export default TaskStats;
