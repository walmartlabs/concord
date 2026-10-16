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

import type { SegmentNode } from './segmentTree';

// Current API has no loop/retry metadata on the owner. Infer only from explicit
// child kinds, never from user-provided names. Empty groups remain ordinary steps.
export const groupLabel = (node: SegmentNode): string | undefined => {
    const iterations = node.children.filter((child) => child.kind === 'iteration').length;
    const attempts = node.children.filter((child) => child.kind === 'attempt').length;
    if (iterations) return `Loop · ${iterations} ${iterations === 1 ? 'iteration' : 'iterations'}`;
    if (attempts) return `Retry · ${attempts} ${attempts === 1 ? 'attempt' : 'attempts'}`;
    if (node.kind === 'error') return 'On error';
    if (node.kind === 'parallel') return 'Parallel';
    return undefined;
};

// A view projection only: messages still belong to the same segment in the API.
export const ownLogNode = (node: SegmentNode): SegmentNode => ({ ...node, children: [] });
