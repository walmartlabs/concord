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

import { isIteration } from './segmentTree';
import type { SegmentNode } from './segmentTree';

// Children supply only the observed count, never the type of the group.
export const groupLabel = (node: SegmentNode): string | undefined => {
    switch (node.kind) {
        case 'loop': {
            const count = node.children.filter(isIteration).length;
            return `Loop · ${count} ${count === 1 ? 'iteration' : 'iterations'}`;
        }
        case 'retry':
        case 'iteration-retry': {
            const count = node.children.filter((child) => child.kind === 'attempt').length;
            const prefix =
                node.kind === 'iteration-retry' ? `Item #${(node.segment.index ?? 0) + 1} · ` : '';
            return `${prefix}Retry · ${count} ${count === 1 ? 'attempt' : 'attempts'}`;
        }
        case 'error':
            return 'On error';
        case 'parallel':
            return 'Parallel';
        default:
            return undefined;
    }
};

// A view projection only: messages still belong to the same segment in the API.
export const ownLogNode = (node: SegmentNode): SegmentNode => ({ ...node, children: [] });
