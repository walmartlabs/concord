/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { useCallback, useState } from 'react';

interface Pagination {
    limit: number;
    offset: number;
}

export interface UsePaginationType {
    paginationFilter: Pagination;
    handleLimitChange: (limit: number) => void;
    handleNext: () => void;
    handlePrev: () => void;
    handleFirst: () => void;
    resetOffset: (offset: number) => void;
}

// TODO customizable defaults (e.g. a way to specify the default "limit")
export const usePagination = (): UsePaginationType => {
    const [paginationFilter, setPaginationFilter] = useState<Pagination>({ offset: 0, limit: 50 });

    const handleLimitChange = useCallback((limit: number) => {
        setPaginationFilter({ offset: 0, limit });
    }, []);

    const handleNext = useCallback(() => {
        setPaginationFilter((prev) => ({ offset: prev.offset + 1, limit: prev.limit }));
    }, []);

    const handlePrev = useCallback(() => {
        setPaginationFilter((prev) => ({ offset: prev.offset - 1, limit: prev.limit }));
    }, []);

    const handleFirst = useCallback(() => {
        setPaginationFilter((prev) => ({ offset: 0, limit: prev.limit }));
    }, []);

    const resetOffset = useCallback((offset: number) => {
        setPaginationFilter((prev) => ({ offset, limit: prev.limit }));
    }, []);

    return {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset
    };
};
