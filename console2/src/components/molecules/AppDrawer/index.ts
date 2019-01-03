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
import SidebarContainer from './Sidebar.Container';
import {
    SidebarShowComponent,
    SidebarToggleComponent,
    SidebarHideComponent
} from './Sidebar.Toggles';
import { Sidebar, as } from 'reakit';

export * from './Sidebar';
export * from './Sidebar.Container';
export * from './Sidebar.Toggles';

// tslint:disable-next-line:prefer-object-spread
export default Object.assign(as(Sidebar)(SidebarContainer), {
    Toggle: SidebarToggleComponent,
    Hide: SidebarHideComponent,
    Show: SidebarShowComponent
});
