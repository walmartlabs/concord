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

import { ConcordId, fetchJson, queryParams } from '../../../api/common';
export interface SegmentLogPage {
    text: string;
    before: number;
    after: number;
    hasPrevious: boolean;
    hasNext: boolean;
}
export interface SegmentLogPageQuery {
    before?: number;
    after?: number;
    limit: number;
}
export const getSegmentLogPage = (
    id: ConcordId,
    segmentId: number,
    query: SegmentLogPageQuery,
    signal?: AbortSignal
) =>
    fetchJson<SegmentLogPage>(
        `/api/v2/process/${id}/log/segment/${segmentId}/data/page?${queryParams(query)}`,
        { signal }
    );
