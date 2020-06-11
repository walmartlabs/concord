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

import * as React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Icon, SemanticCOLORS, SemanticICONS } from 'semantic-ui-react';

import { SegmentStatus } from '../../../api/process/log';
import { ConcordId } from '../../../api/common';

import './styles.css';
import { formatDistance, parseISO } from 'date-fns';

interface Props {
    instanceId: ConcordId;
    segmentId: number;
    name: string;
    createdAt: string;
    status?: SegmentStatus;
    warnings?: number;
    errors?: number;
    data: string[];
    onStartLoading: (isLoadWholeLog: boolean) => void;
    onStopLoading: () => void;
    onSegmentInfo?: () => void;
    loading: boolean;
    open?: boolean;
}

const LogSegment = ({
    instanceId,
    segmentId,
    name,
    createdAt,
    status,
    warnings,
    errors,
    data,
    onStartLoading,
    onStopLoading,
    onSegmentInfo,
    loading,
    open
}: Props) => {
    const scrollAnchorRef = useRef<HTMLDivElement>(null);

    const [isOpen, setOpen] = useState<boolean>(!!open);
    const [isLoadAll, setLoadAll] = useState<boolean>(false);
    const [isAutoScroll, setAutoScroll] = useState<boolean>(false);

    const loadAllClickHandler = useCallback((ev: React.MouseEvent<any>) => {
        ev.preventDefault();
        ev.stopPropagation();
        setLoadAll((prevState) => !prevState);
    }, []);

    const segmentInfoClickHandler = useCallback(
        (event: React.MouseEvent<any>) => {
            event.stopPropagation();
            if (onSegmentInfo !== undefined) {
                onSegmentInfo();
            }
        },
        [onSegmentInfo]
    );

    const autoscrollClickHandler = useCallback((ev: React.MouseEvent<any>) => {
        ev.preventDefault();
        ev.stopPropagation();
        setAutoScroll((prevState) => !prevState);
    }, []);

    useEffect(() => {
        if (isOpen) {
            onStartLoading(isLoadAll);
        } else {
            onStopLoading();
        }
    }, [isOpen, isLoadAll, name, onStartLoading, onStopLoading]);

    useEffect(() => {
        if (isAutoScroll && scrollAnchorRef.current !== null) {
            scrollAnchorRef.current.scrollIntoView();
        }
    }, [isAutoScroll, data]);

    const hasWarnings = !!(warnings && warnings > 0);
    const hasErrors = !!(errors && errors > 0);

    const createdAtDate = parseISO(createdAt);
    let beenRunningFor;
    if (status === SegmentStatus.RUNNING) {
        beenRunningFor = formatDistance(new Date(), createdAtDate);
    }

    return (
        <div className="LogSegment">
            <Button
                fluid={true}
                size="medium"
                className="Segment"
                onClick={() => setOpen((prevState) => !prevState)}>
                <Icon name={isOpen ? 'caret down' : 'caret right'} className="State" />

                <StatusIcon status={status} loading={loading} warnings={warnings} errors={errors} />

                <span className="Caption">{name}</span>

                {(hasWarnings || hasErrors) && (
                    <>
                        <span className="Counter">warn: {warnings ? warnings : 0}</span>
                        <span className="Counter">error: {errors ? errors : 0}</span>
                    </>
                )}

                {beenRunningFor && <span className="RunningFor">running for {beenRunningFor}</span>}

                <a
                    href={`/api/v2/process/${instanceId}/log/segment/${segmentId}/data`}
                    onClick={(event) => event.stopPropagation()}
                    rel="noopener noreferrer"
                    target="_blank"
                    title="Pop out"
                    className="AdditionalAction Last">
                    <Icon name="external alternate" />
                </a>

                {onSegmentInfo !== undefined && (
                    <div className={'AdditionalAction'}>
                        <Icon name={'info'} title={'Show info'} onClick={segmentInfoClickHandler} />
                    </div>
                )}

                {isOpen && (
                    <>
                        <div className="AdditionalAction">
                            <div
                                className={isAutoScroll ? 'on' : 'off'}
                                onClick={autoscrollClickHandler}>
                                Auto Scroll
                            </div>
                        </div>

                        <div className="AdditionalAction">
                            <div className={isLoadAll ? 'on' : 'off'} onClick={loadAllClickHandler}>
                                Load All
                            </div>
                        </div>
                    </>
                )}
            </Button>

            {isOpen && (
                <div className="ContentContainer">
                    <div className="InnerContentContainer">
                        <div className="Content">
                            {data.map((value, index) => (
                                <pre key={index} dangerouslySetInnerHTML={{ __html: value }} />
                            ))}
                        </div>
                        <div ref={scrollAnchorRef} />
                    </div>
                </div>
            )}
        </div>
    );
};

interface StatusIconProps {
    status?: SegmentStatus;
    loading?: boolean;
    warnings?: number;
    errors?: number;
}

const StatusIcon = ({ status, loading, warnings = 0, errors = 0 }: StatusIconProps) => {
    if (!status) {
        return <span className="EmptyStatus" />;
    }

    let color: SemanticCOLORS = 'green';
    let icon: SemanticICONS = 'circle';
    let spinning = false;

    if (status === SegmentStatus.RUNNING || loading) {
        icon = 'spinner';
        spinning = true;
    } else if (status === SegmentStatus.FAILED) {
        color = 'red';
        icon = 'close';
    } else if (warnings > 0) {
        color = 'orange';
        icon = 'exclamation circle';
    } else if (errors > 0) {
        color = 'red';
        icon = 'exclamation circle';
    }

    return <Icon loading={spinning} name={icon} color={color} className="Status" />;
};

export default LogSegment;
