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
import { useEffect, useState } from 'react';

import { ConcordId } from '../../../api/common';
import { listStepEvents, ProcessElementEvent } from '../../../api/process/event';
import { LogSegmentEntry } from '../../../api/process/log';
import { ReactJson } from '../../atoms';

interface Props {
    instanceId: ConcordId;
    // the segment of the task call
    run: LogSegmentEntry;
}

type Vars = Record<string, unknown>;

const asVars = (v: unknown): Vars | undefined =>
    v !== null && typeof v === 'object' && !Array.isArray(v) ? (v as Vars) : undefined;

/**
 * Input and output of a task call. Requires access to the extended event data ("includeAll").
 */
const CallData = ({ instanceId, run }: Props) => {
    const [state, setState] = useState<{ input?: Vars; output?: Vars; error?: string }>();

    useEffect(() => {
        if (!run.correlationId) {
            return;
        }

        let cancelled = false;
        setState(undefined);
        listStepEvents<ProcessElementEvent>(
            instanceId,
            {
                segmentId: run.id,
                correlationId: run.correlationId,
                repeated: run.attempt !== undefined || run.loopIndex !== undefined,
            },
            true
        )
            .then((events) => {
                if (cancelled) {
                    return;
                }
                const pre = events.find((e) => e.data.phase === 'pre')?.data;
                const post = events.find((e) => e.data.phase === 'post')?.data;
                setState({
                    input: asVars(post?.in) ?? asVars(pre?.in),
                    output: asVars(post?.out),
                });
            })
            .catch((e) => {
                if (!cancelled) {
                    setState({ error: (e as { message?: string })?.message ?? String(e) });
                }
            });

        return () => {
            cancelled = true;
        };
    }, [instanceId, run.id, run.correlationId, run.attempt, run.loopIndex, run.status]);

    if (state === undefined) {
        return <div className="CallData Muted">Loading the call details...</div>;
    }

    if (state.error) {
        return (
            <div className="CallData Muted">The call details are not available: {state.error}</div>
        );
    }

    return (
        <div className="CallData">
            <Section title="Input" vars={state.input} />
            <Section title="Output" vars={state.output} />
        </div>
    );
};

const Section = ({ title, vars }: { title: string; vars?: Vars }) => {
    const keys = vars ? Object.keys(vars).sort() : [];
    return (
        <div className="Card">
            <div className="CardTitle">
                {title}
                <span className="Count">
                    {keys.length === 0
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
