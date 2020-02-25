/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import { ConcordKey, EntityOwner, fetchJson, queryParams } from '../common';

// must match the keys of com.walmartlabs.concord.server.audit.AuditObject enum
export enum AuditObject {
    EXTERNAL_EVENT = 'EXTERNAL_EVENT',
    JSON_STORE = 'JSON_STORE',
    JSON_STORE_DATA = 'JSON_STORE_DATA',
    JSON_STORE_QUERY = 'JSON_STORE_QUERY',
    ORGANIZATION = 'ORGANIZATION',
    PROJECT = 'PROJECT',
    SECRET = 'SECRET',
    TEAM = 'TEAM'
}

// must match the keys of com.walmartlabs.concord.server.audit.AuditAction enum
export enum AuditAction {
    CREATE = 'CREATE',
    UPDATE = 'UPDATE',
    DELETE = 'DELETE',
    ACCESS = 'ACCESS'
}

// must match the allowed keys in AuditLogResource#ALLOWED_DETAILS_KEYS
export interface AuditLogFilter {
    object?: AuditObject;
    action?: AuditAction;
    username?: string;
    after?: string;
    before?: string;
    details?: {
        eventId?: string;
        fullRepoName?: string;
        githubEvent?: string;
        source?: string;
        orgName?: ConcordKey;
        projectName?: ConcordKey;
        secretName?: ConcordKey;
        jsonStoreName?: ConcordKey;
        teamName?: ConcordKey;
    };
    offset?: number; // TODO rename to "page"?
    limit?: number;
}

export interface AuditLogEntry {
    entryDate: string;
    action: string;
    object: string;
    details: {};
    user?: EntityOwner;
}

export interface PaginatedAuditLogEntries {
    items: AuditLogEntry[];
    next: boolean;
}

export const list = async (filter: AuditLogFilter): Promise<PaginatedAuditLogEntries> => {
    const { offset = 0, limit = 50 } = filter;

    const offsetParam = offset > 0 && limit > 0 ? offset * limit : offset;
    const limitParam = limit > 0 ? limit + 1 : limit;

    // convert the `details` object into a set of `details.key=value` query parameters
    const details = filter.details || {};

    const detailsParam = {};
    Object.keys(details).forEach((k) => (detailsParam[`details.${k}`] = details[k]));

    const data: AuditLogEntry[] = await fetchJson(
        `/api/v1/audit?${queryParams({
            ...detailsParam,
            object: filter.object,
            action: filter.action,
            username: filter.username,
            after: filter.after,
            before: filter.before,
            offset: offsetParam,
            limit: limitParam
        })}`
    );

    const hasMoreElements: boolean = !!limit && data.length > limit;

    if (limit > 0 && hasMoreElements) {
        // TODO we should trim the list to the size instead of removing a single element
        data.pop();
    }

    return {
        items: data,
        next: hasMoreElements
    };
};
