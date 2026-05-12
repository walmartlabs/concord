/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import type { ComponentType } from 'react';
import ReactJsonModule, { type ReactJsonViewProps } from 'react-json-view';

type ReactJsonModuleShape = ComponentType<ReactJsonViewProps> & {
    default?: ComponentType<ReactJsonViewProps>;
};

// Vite 8 can surface this UMD dependency as either the component or a module object.
const ReactJson =
    (ReactJsonModule as ReactJsonModuleShape).default ??
    (ReactJsonModule as ReactJsonModuleShape);

export default ReactJson;
