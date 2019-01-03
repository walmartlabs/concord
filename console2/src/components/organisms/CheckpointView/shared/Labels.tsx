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
import styled from 'styled-components';
import { ClassIcon } from '../../../atoms/ClassIcon';

const TextBase = styled.span`
    font-family: lato;
    color: #706f70;
`;

export const Label = styled(TextBase)`
    font-weight: bold;
    font-size: 1rem;
`;

export const StatusText = styled(TextBase)`
    font-size: 1rem;
    display: inline;
`;

export const CheckpointName = styled(TextBase)`
    font-size: 1rem;
    font-weight: bold;
    margin-bottom: 4px;
`;

export const CheckpointGroupName = styled(CheckpointName)`
    font-size: 1.2rem;
    font-weight: bold;
`;

export const LoadError = styled.div`
    color: grey;
    font-weight: bold;
    font-size: 1.2rem;
    margin: auto auto;
    padding: 1em;
`;

export const Status: React.SFC<{ as?: 'span' | 'div' | 'td' }> = ({ as = 'span', children }) => {
    switch (children) {
        case 'FAILED':
            return React.createElement(
                as,
                { style: { color: '#DB2928' } },
                <>
                    {children} <ClassIcon classes="red cancel icon" />
                </>
            );
        case 'FINISHED':
            return React.createElement(
                as,
                { style: { color: 'green' } },
                <>
                    {children} <ClassIcon classes="green check icon" />
                </>
            );
        default:
            return React.createElement(as, null, children);
    }
};
