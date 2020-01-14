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

import { PaginatedWaitHistoryEntries, ProcessWaitHistoryEntry } from '../';
import { ConcordId, fetchJson, queryParams } from '../../common';

export const get = async (
    instanceId: ConcordId,
    page: number,
    limit: number
): Promise<PaginatedWaitHistoryEntries> => {
    const offsetParam = page > 0 && limit > 0 ? page * limit : page;
    const limitParam = limit > 0 ? limit + 1 : limit;

    const data: ProcessWaitHistoryEntry[] = await fetchJson(
        `/api/v1/process/${instanceId}/waits?${queryParams({
            offset: offsetParam,
            limit: limitParam
        })}`
    );

    const hasMoreElements: boolean = limit > 0 && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements
    };
};
