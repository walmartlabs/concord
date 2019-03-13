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
import { styled } from 'reakit';

export const Row = styled.div`
    /* display: grid;
    grid-template-columns: fit-content(300px) 1fr; */

    /* // TODO: Determine a better way to handle long repoMetadata */

    display: flex;
    flex-direction: row;
    flex-wrap: nowrap;

    width: 100%;
    margin: 24px 0px;
    border-radius: 5px;
    box-shadow: 0 1px 2px 0 rgba(34, 36, 38, 0.15);
`;

interface ColumnProps {
    flex?: number;
    background?: string;
    maxWidth?: number;
}

export const Column = styled('div')<ColumnProps>`
    display: flex;
    flex-direction: column;
    flex-basis: 100%;
    flex: ${(props: ColumnProps) => (props.flex ? props.flex : 1)};
    background-color: ${(props: ColumnProps) => props.background};
    ${(props: ColumnProps) => (props.maxWidth ? `max-width: ${props.maxWidth}px` : null)};
`;
