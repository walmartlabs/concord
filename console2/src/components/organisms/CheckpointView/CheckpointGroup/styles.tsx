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
import styled from 'styled-components';
import { ProcessStatus } from '../../../../api/process';
import { SemanticCOLORS } from 'semantic-ui-react';

export const FlexWrapper = styled.div`
    height: 100%;
    display: flex;
    flex-direction: row;
    justify-content: left;
    padding: 2rem 1.5rem;
    white-space: nowrap;
`;

export const GroupWrapper = styled.div`
    margin: auto 1rem;
`;

export const CheckpointNode = styled.div`
    cursor: pointer;
    padding: 4px;
`;

export const getStatusColor = (status: string): string => {
    switch (status) {
        case ProcessStatus.NEW:
        case ProcessStatus.PREPARING:
        case ProcessStatus.RUNNING:
        case ProcessStatus.STARTING:
        case ProcessStatus.SUSPENDED:
            return '#4182C3';
        case ProcessStatus.FINISHED:
            return '#0ca934';
        case ProcessStatus.CANCELLED:
        case ProcessStatus.FAILED:
        case ProcessStatus.TIMED_OUT:
            return '#DB2928';
        case ProcessStatus.ENQUEUED:
        case ProcessStatus.RESUMING:
        case ProcessStatus.WAITING:
        default:
            return 'grey';
    }
};

export const getStatusButtonColor = (status: string): SemanticCOLORS => {
    switch (status) {
        case ProcessStatus.NEW:
        case ProcessStatus.PREPARING:
        case ProcessStatus.RUNNING:
        case ProcessStatus.STARTING:
        case ProcessStatus.SUSPENDED:
            return 'blue';
        case ProcessStatus.FINISHED:
            return 'green';
        case ProcessStatus.CANCELLED:
        case ProcessStatus.FAILED:
        case ProcessStatus.TIMED_OUT:
            return 'red';
        case ProcessStatus.ENQUEUED:
        case ProcessStatus.RESUMING:
        case ProcessStatus.WAITING:
        default:
            return 'grey';
    }
};

interface CheckpointBoxProps {
    statusColor?: any;
}

export const CheckpointBox = styled('div')<CheckpointBoxProps>`
    background: ${(prop: CheckpointBoxProps) => (prop.statusColor ? prop.statusColor : 'gray')};
    padding: 0.7rem 0.7rem;
    text-align: center;
    border-radius: 5px;
    color: #ffffff;
    font-size: 1rem;
    font-weight: 900;
`;

export const EmptyBox = styled('div')`
    background: lightgray;
    padding: 0.7rem 0.7rem;
    text-align: center;
    border-radius: 5px;
    color: #ffffff;
    font-size: 1rem;
    font-weight: 900;
`;

export const GroupItems = styled.div`
    display: flex;
    flex-direction: row;
    border-left: 1px solid gray;
    padding: 0 0.5rem;
    margin: 1rem;
`;
