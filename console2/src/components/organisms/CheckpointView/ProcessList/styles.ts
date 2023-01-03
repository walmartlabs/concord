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
import { Column } from '../shared/Layout';

export const LeftWrap = styled(Column)`
    border: 1px solid #dedfde;
    border-top-left-radius: 5px;
    border-bottom-left-radius: 5px;
    float: left;
`;

export const ContentBlock = styled(Column)`
    border: 1px solid #dedfde;
    border-radius: 5px;
    overflow-y: hidden;
    overflow-x: auto;

    --shadow-height: 100%;
    --shadow-color: rgba(0, 0, 0, 0.1);
    --shadow-weight: 9px;

    /* Left start and right start 'inside' container colors (they overlap the shadows) */
    background: linear-gradient(90deg, white 0%, rgba(255, 255, 255, 0)),
        linear-gradient(-90deg, white 0%, rgba(255, 255, 255, 0)) 100% 0,
        /* Left and right scroll shadows */
            linear-gradient(90deg, var(--shadow-color), rgba(0, 0, 0, 0)),
        linear-gradient(-90deg, var(--shadow-color), rgba(0, 0, 0, 0)) 100% 0;
    background-repeat: no-repeat;
    background-color: #fff;
    background-size: 100px 100%, 100px 100%, var(--shadow-weight) var(--shadow-height),
        var(--shadow-weight) var(--shadow-height);
    background-attachment: local, local, scroll, scroll;

    /* Scrollbar has a bit of a custom look */
    &::-webkit-scrollbar {
        height: 6px;
    }

    &::-webkit-scrollbar-track {
        box-shadow: inset 0 0 4px rgba(0, 0, 0, 0.3);
        border-radius: 10px;
    }

    &::-webkit-scrollbar-thumb {
        border-radius: 10px;
        box-shadow: inset 0 0 4px rgba(0, 0, 0, 0.5);
    }
`;

export const RightWrap = styled(ContentBlock)`
    border-top-left-radius: 0px;
    border-bottom-left-radius: 0px;
`;

export const ListItem = styled('li')`
    font-family: lato;
    text-align: left;
    list-style-type: none;
    color: #706f70;

    padding: 16px;

    i {
        padding: 0px 8px;
        display: inline;
        position: relative;
        bottom: 2px;
    }
`;
