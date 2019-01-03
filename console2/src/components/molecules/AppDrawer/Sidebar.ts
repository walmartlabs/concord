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
import { styled, Sidebar } from 'reakit';

const StyledSidebar = styled(Sidebar)`
    background-color: #ffffff;
    padding: 2em;
    box-shadow: 0 7px 10px 0 rgba(0, 0, 0, 0.5);

    @media (max-width: 2400px) {
        max-width: 50vw;
    }

    @media (max-width: 1600px) {
        max-width: 75%;
    }

    @media (max-width: 800px) {
        max-width: 100vw;
    }
`;

export default StyledSidebar;
