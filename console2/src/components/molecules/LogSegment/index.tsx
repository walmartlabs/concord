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
import {
    formatDistance,
    formatDuration,
    intervalToDuration,
    parseISO as parseDate,
} from 'date-fns';
import { Link, useLocation } from 'react-router-dom';

import { SegmentStatus } from '../../../api/process/log';
import { ConcordId } from '../../../api/common';
import {
    getStatusSemanticColor,
    getStatusSemanticIcon,
    isFinal,
    ProcessStatus,
} from '../../../api/process';

import './styles.css';

interface Props {
    instanceId: ConcordId;
    segmentId: number;
    name: string;
    createdAt: string;
    processStatus?: ProcessStatus;
    status?: SegmentStatus;
    statusUpdatedAt?: string;
    lowRange?: number;
    warnings?: number;
    errors?: number;
    data: string[];
    onStartLoading: (isLoadWholeLog: boolean) => void;
    onStopLoading: () => void;
    onSegmentInfo?: () => void;
    loading: boolean;
    forceOpen: boolean;
}

const LogSegment = ({
    instanceId,
    segmentId,
    name,
    createdAt,
    processStatus,
    status,
    statusUpdatedAt,
    lowRange,
    warnings,
    errors,
    data,
    onStartLoading,
    onStopLoading,
    onSegmentInfo,
    loading,
    forceOpen,
}: Props) => {
    const scrollAnchorRef = useRef<HTMLDivElement>(null);
    const location = useLocation();
    const [isOpen, setOpen] = useState<boolean>(forceOpen);
    const [isLoadAll, setLoadAll] = useState<boolean>(false);
    const [isAutoScroll, setAutoScroll] = useState<boolean>(false);

    const baseUrl = `/process/${instanceId}/log`;

    const myRef = useRef<null | HTMLDivElement>(null);

    useEffect(() => {
        if (myRef.current && location.hash.includes(`#segmentId=${segmentId}`)) {
            myRef.current.scrollIntoView({
                behavior: 'smooth',
                block: 'end',
                inline: 'nearest',
            });
            setOpen(true);
        }
    }, [myRef, segmentId, location]);

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
        setOpen(forceOpen);
    }, [forceOpen]);

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

    const createdAtDate = parseDate(createdAt);

    let beenRunningFor;
    if (status === SegmentStatus.RUNNING && !isFinal(processStatus)) {
        beenRunningFor = formatDistance(new Date(), createdAtDate);
    }

    let wasRunningFor;
    if (status !== SegmentStatus.RUNNING && statusUpdatedAt) {
        const statusUpdatedAtDate = parseDate(statusUpdatedAt);
        wasRunningFor = formatDuration(
            intervalToDuration({
                start: createdAtDate,
                end: statusUpdatedAtDate,
            })
        );
    }

    return (
        <div className="LogSegment" id={`segmentId=${segmentId}`} ref={myRef}>
            <Button
                fluid={true}
                size="medium"
                className="Segment"
                onClick={() => setOpen((prevState) => !prevState)}
            >
                <Icon name={isOpen ? 'caret down' : 'caret right'} className="State" />

                <StatusIcon
                    status={status}
                    processStatus={processStatus}
                    warnings={warnings}
                    errors={errors}
                />

                <span className="Caption">{name}</span>

                {(hasWarnings || hasErrors) && (
                    <>
                        <span className="Counter">warn: {warnings ? warnings : 0}</span>
                        <span className="Counter">error: {errors ? errors : 0}</span>
                    </>
                )}

                {beenRunningFor && <span className="RunningFor">running for {beenRunningFor}</span>}
                {wasRunningFor && <span className="RunningFor">{wasRunningFor}</span>}

                <Link
                    to={`${baseUrl}#segmentId=${segmentId}`}
                    className="AdditionalAction Anchor"
                    data-tooltip="Hyperlink"
                    data-inverted=""
                >
                    <Icon name="linkify" />
                </Link>

                <a
                    href={`/api/v2/process/${instanceId}/log/segment/${segmentId}/data`}
                    onClick={(event) => event.stopPropagation()}
                    rel="noopener noreferrer"
                    target="_blank"
                    className="AdditionalAction Last"
                    data-tooltip="Download: InstanceId_SegmentId.log"
                    data-inverted=""
                >
                    <Icon name="download" />
                </a>

                {onSegmentInfo !== undefined && (
                    <div className={'AdditionalAction'} data-tooltip="Show Info" data-inverted="">
                        <Icon
                            name={'info circle'}
                            title={'Show info'}
                            onClick={segmentInfoClickHandler}
                        />
                    </div>
                )}

                {isOpen && (
                    <>
                        <div className="AdditionalAction">
                            <div
                                className={isAutoScroll ? 'on' : 'off'}
                                data-tooltip="Auto Scroll"
                                data-inverted=""
                            >
                                <Icon name={'angle double down'} onClick={autoscrollClickHandler} />
                            </div>
                        </div>
                        <div className="AdditionalAction">
                            <div
                                className={isLoadAll ? 'on' : 'off'}
                                data-tooltip="Show Full Log"
                                data-inverted=""
                            >
                                <Icon
                                    name={'arrows alternate vertical'}
                                    onClick={loadAllClickHandler}
                                />
                            </div>
                        </div>
                    </>
                )}
                {loading && <div className="Loader" />}
            </Button>

            {isOpen && (
                <div className="ContentContainer">
                    <div className="InnerContentContainer">
                        <div className="Content">
                            {lowRange !== undefined && lowRange !== 0 && (
                                <>
                                    <span>...showing only the last few lines. </span>
                                    {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
                                    <a href="#" onClick={loadAllClickHandler}>
                                        Full log
                                    </a>{' '}
                                </>
                            )}

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
    processStatus?: ProcessStatus;
    loading?: boolean;
    warnings?: number;
    errors?: number;
}

const StatusIcon = ({ status, processStatus, warnings = 0, errors = 0 }: StatusIconProps) => {
    if (!status) {
        return (
            <Icon
                loading={
                    processStatus !== ProcessStatus.SUSPENDED &&
                    processStatus !== ProcessStatus.FAILED &&
                    processStatus !== ProcessStatus.FINISHED &&
                    processStatus !== ProcessStatus.CANCELLED &&
                    processStatus !== ProcessStatus.TIMED_OUT
                }
                name={processStatus ? getStatusSemanticIcon(processStatus) : 'circle'}
                color={processStatus ? getStatusSemanticColor(processStatus) : 'grey'}
                className="Status"
            />
        );
    }

    let color: SemanticCOLORS = 'green';
    let icon: SemanticICONS = 'circle';
    let spinning = false;

    if (status === SegmentStatus.RUNNING && isFinal(processStatus)) {
        color = 'yellow';
        icon = 'question circle';
    } else if (status === SegmentStatus.RUNNING) {
        color = 'teal';
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
