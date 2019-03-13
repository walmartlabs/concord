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

export const FullBar = styled.div`
    width: 100%;
    min-height: 2.85714286em;

    display: flex;
    vertical-align: middle;

    margin-top: 1rem;
    margin-bottom: 1rem;

    border: 1px solid #d4d4d5;
    border-radius: 5px;

    @media (min-width: 769px) {
        align-items: center;
    }

    @media (max-width: 768px) {
        align-items: left;
        flex-direction: column;
    }
`;

export const Item = styled.div`
    padding: 0.92857143em 1.14285714em;
    position: relative;
    vertical-align: middle;
    line-height: 1;
    text-decoration: none;

    ${({ pushRight }: { pushRight?: boolean }) => (pushRight ? `margin-left: auto` : ``)}
    @media (max-width: 768px) {
        margin-left: initial;
        align-items: left;
        flex-direction: column;
    }
`;
