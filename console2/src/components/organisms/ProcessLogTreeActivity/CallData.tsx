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

import * as React from 'react';
import { ProcessStatus } from '../../../api/process';

import { ConcordId } from '../../../api/common';
import {
    ProcessElementEvent,
    ProcessEventEntry,
    VariableMapping,
} from '../../../api/process/event';
import { LogSegmentEntry } from '../../../api/process/log';
import { ReactJson } from '../../atoms';
import { useStepEvents } from './useStepEvents';

interface Props {
    instanceId: ConcordId;
    // the segment of the task call
    run: LogSegmentEntry;
    processStatus?: ProcessStatus;
}

type Vars = Record<string, unknown>;

const asVars = (v: unknown): Vars | undefined => {
    if (Array.isArray(v)) {
        return v.reduce<Vars>((result, item: VariableMapping) => {
            if (item && typeof item.target === 'string') {
                result[item.target] =
                    item.resolved !== undefined ? item.resolved : item.sourceValue;
            }
            return result;
        }, {});
    }
    return v !== null && typeof v === 'object' ? (v as Vars) : undefined;
};

const eventPair = (events: ProcessEventEntry<ProcessElementEvent>[]) => {
    let post: ProcessElementEvent | undefined;
    for (let i = events.length - 1; i >= 0; i -= 1) {
        const event = events[i].data;
        if (!post && event.phase === 'post') {
            post = event;
        } else if (event.phase === 'pre') {
            return { pre: event, post };
        }
    }
    return { pre: undefined, post };
};

/**
 * Input and output of a task call. Requires access to the extended event data ("includeAll").
 */
const CallData = ({ instanceId, run, processStatus }: Props) => {
    const { events, error, exhausted, refresh } = useStepEvents(instanceId, run, {
        includeAll: true,
        enabled: true,
        waitForPost: true,
        processStatus,
    });

    if (events === undefined && !error) {
        return <div className="CallData Muted">Loading the call details...</div>;
    }

    const { pre, post } = eventPair(events ?? []);
    const input = asVars(post?.in) ?? asVars(pre?.in);
    const output = asVars(post?.out);
    const hasData = pre !== undefined || post !== undefined;

    return (
        <div className="CallData">
            {error && (
                <div className="EventState" role="alert">
                    The call details are not available: {error.message}
                    {error.details && <span className="Details"> {error.details}</span>}
                    <button type="button" onClick={refresh}>
                        Retry
                    </button>
                </div>
            )}
            {exhausted && !post && (
                <div className="EventState">
                    Complete call details have not arrived yet.
                    <button type="button" onClick={refresh}>
                        Refresh details
                    </button>
                </div>
            )}
            {hasData ? (
                <>
                    <Section title="Input" vars={input} />
                    <Section title="Output" vars={output} pending={!post} />
                </>
            ) : (
                !error && !exhausted && <div className="Muted">Waiting for the call details...</div>
            )}
        </div>
    );
};

const Section = ({ title, vars, pending }: { title: string; vars?: Vars; pending?: boolean }) => {
    const keys = vars ? Object.keys(vars).sort() : [];
    return (
        <div className="Card">
            <div className="CardTitle">
                {title}
                <span className="Count">
                    {pending
                        ? 'waiting'
                        : keys.length === 0
                        ? 'none'
                        : `${keys.length} ${keys.length === 1 ? 'parameter' : 'parameters'}`}
                </span>
            </div>
            {keys.map((k) => (
                <div className="Param" key={k}>
                    <span className="Key" title={k}>
                        {k}
                    </span>
                    <span className="Value">
                        <Value value={vars![k]} />
                    </span>
                </div>
            ))}
        </div>
    );
};

// values are colored by type, like in a code editor
const Value = ({ value }: { value: unknown }) => {
    if (value === null || value === undefined) {
        return <span className="Null">null</span>;
    }
    if (typeof value === 'object') {
        return (
            <ReactJson
                src={value as object}
                name={null}
                collapsed={true}
                enableClipboard={false}
                displayDataTypes={false}
                style={{ background: 'transparent', fontSize: 12 }}
            />
        );
    }
    if (typeof value === 'string') {
        return <span className="String">{value}</span>;
    }
    return (
        <span className={typeof value === 'number' ? 'Number' : 'Boolean'}>{String(value)}</span>
    );
};

export default CallData;
