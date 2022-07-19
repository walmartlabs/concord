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
import React  from 'react';
import { Button, Popup, Grid } from 'semantic-ui-react';

import ClassIcon from '../../../atoms/ClassIcon';
import Truncate from '../../../atoms/Truncate';

import RestoreProcess from './RestoreProcess';
import { CheckpointName } from '../shared/Labels';
import { CustomCheckpoint } from '../shared/types';
import { ProcessEntry, isFinal } from '../../../../api/process';
import { format as formatDate } from 'date-fns';
import {useCheckpointContext} from "../Container";
import {useLogContext} from "../../../molecules/ProcessLogContainer/LogContainer";

const CheckpointPopup: React.SFC<{
    checkpoint: CustomCheckpoint;
    process: ProcessEntry;
    render: React.ReactNode;
}> = ({ checkpoint, process, render }) => {
    const { currentPage, limitPerPage, loadData, orgId, projectId } = useCheckpointContext();
    useLogContext();

    return (
        <Popup
            inverted={false}
            trigger={<div>{render}</div>}
            on={['click', 'hover']}
            closeOnTriggerClick={true}
            hoverable={true}
            hideOnScroll={true}
            position="top center"
            flowing={true}
            style={{ maxWidth: '300px' }}
            content={
                <>
                    <Grid divided={true} columns={2}>
                        <Grid.Row style={{ minWidth: '20em' }}>
                            <Grid.Column>
                                <CheckpointName>
                                    <Truncate text={checkpoint.name} />
                                </CheckpointName>
                                <br />
                            </Grid.Column>
                            <Grid.Column
                                style={{
                                    whiteSpace: 'nowrap',
                                    paddingRight: '1em'
                                }}>
                                <CheckpointName>
                                    {checkpoint.startTime.toDateString()}
                                    <br />
                                    <span
                                        style={{
                                            fontWeight: 300,
                                            paddingRight: '1em'
                                        }}>
                                        {formatDate(checkpoint.startTime, 'HH:mm:ss')}
                                    </span>
                                </CheckpointName>
                            </Grid.Column>
                        </Grid.Row>
                    </Grid>
                    <hr />
                    <RestoreProcess>
                        {({ restoreProcess }: any) => (
                            <Button
                                primary={true}
                                disabled={!isFinal(process.status) || process.disabled}
                                loading={process.status === 'ENQUEUED'}
                                onClick={() => {
                                    restoreProcess(process.instanceId, checkpoint.id);
                                    // // TODO: ಠ_ಠ ... hack, needs to be better.
                                    setTimeout(() => {
                                        loadData({
                                            orgId,
                                            projectId,
                                            limit: limitPerPage,
                                            offset: (currentPage - 1) * limitPerPage
                                        });
                                    }, 200);
                                }}>
                                <ClassIcon
                                    classes="icon redo white"
                                    style={{
                                        fontSize: '0.7rem',
                                        fontWeight: 'bold'
                                    }}
                                />
                                Restore this Checkpoint
                            </Button>
                        )}
                    </RestoreProcess>
                </>
            }
        />
    );
};

export default CheckpointPopup;
