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
import styled from 'styled-components';

export const Table = styled.div`
    display: flex;
    flex-direction: row;
`;

const group = styled.div`
    &:first-child {
        margin-top: 0px!;
    };

    &:last-child {
        margin-bottom: 0px!;
    }
`;

export const Keys = styled(group)`
    flex-shrink: 1;
`;

export const Values = styled(group)`
    flex-grow: 1;
    margin-left: 15px;
`;

export const Key = styled.div`
    font-weight: bold;
    text-align: right;
    margin: 5px 0px;
    &::after {
        content: ":"
    }
`;

export const Value = styled.div`
    margin: 5px 0px;
`;

export const Input = styled.input`
    border: #2185D0;
    padding: 0px;
    border-bottom-style: dashed;
    border-width: 1px;
    &:focus {
        outline: none;
    }
`;
