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
import { useLocation } from 'react-router-dom';

import {LogSegmentEntry, SegmentStatus} from '../../../api/process/log';
import { ConcordId } from '../../../api/common';
import {
    getStatusSemanticColor,
    getStatusSemanticIcon,
    isFinal,
    ProcessStatus,
} from '../../../api/process';

import './styles.css';
import LogSegmentActivityV3 from "../../organisms/ProcessLogActivityV3/LogSegmentActivity";

interface Props {
    instanceId: ConcordId;
    segmentId: number;
    parentSegmentId?: number;
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

    childSegments?: LogSegmentEntry[];
    meta?: object;
}

const LogSegmentV3 = ({
    instanceId,
    segmentId,
    name,
    processStatus,
    status,
    lowRange,
    warnings,
    errors,
    data,
    onStartLoading,
    onStopLoading,
    onSegmentInfo,
    loading,
    forceOpen,
    childSegments,
    meta
}: Props) => {
    const scrollAnchorRef = useRef<HTMLDivElement>(null);
    const location = useLocation();
    const [isOpen, setOpen] = useState<boolean>(forceOpen);
    const [isLoadAll, setLoadAll] = useState<boolean>(false);

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
                    meta={meta}
                    segmentInfoClickHandler={segmentInfoClickHandler}
                />

                <span className="Caption">{name}</span>

                {meta && meta['loopIndex'] !== undefined &&
                    <span className="RunningFor"># {meta['loopIndex']}</span>}

                {/*{beenRunningFor && <span className="RunningFor">running for {beenRunningFor}</span>}*/}
                {/*{wasRunningFor && <span className="RunningFor">{wasRunningFor}</span>}*/}

                {/*{onSegmentInfo !== undefined && (*/}
                {/*    <div className={'AdditionalAction'} data-tooltip="Show Info" data-inverted="">*/}
                {/*        <Icon*/}
                {/*            name={'info circle'}*/}
                {/*            title={'Show info'}*/}
                {/*            onClick={segmentInfoClickHandler}*/}
                {/*        />*/}
                {/*    </div>*/}
                {/*)}*/}

                {loading && <div className="Loader" />}
            </Button>

            {isOpen && childSegments &&
                childSegments
                .map((s) => {
                    return (
                        <LogSegmentActivityV3
                            instanceId={instanceId}
                            segmentId={s.id}
                            correlationId={s.correlationId}
                            name={s.name}
                            createdAt={s.createdAt}
                            status={s.status}
                            statusUpdatedAt={s.statusUpdatedAt}
                            warnings={s.warnings}
                            errors={s.errors}
                            processStatus={processStatus}
                            opts={{useLocalTime: true}}
                            forceRefresh={false}
                            key={s.id}
                            forceOpen={forceOpen}
                            meta={s.meta}
                        />
                    );
                })

            }

            {isOpen && data.length > 0 && (
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
    meta?: object;
    segmentInfoClickHandler: (event: React.MouseEvent<any>) => void
}

const StatusIcon = ({ status, processStatus, warnings = 0, errors = 0, meta, segmentInfoClickHandler }: StatusIconProps) => {
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
    let changeIcon = true;

    if (meta && meta['type'] === 'task') {
        icon = 'cog';
        changeIcon = false;
    } else if (meta && meta['type'] === 'call') {
        icon = 'folder outline';
        changeIcon = false;
    }

    if (status === SegmentStatus.RUNNING && isFinal(processStatus)) {
        color = 'yellow';
        icon = 'question circle';
    } else if (status === SegmentStatus.RUNNING) {
        color = 'teal';
        icon = 'spinner';
        spinning = true;
    } else if (status === SegmentStatus.FAILED) {
        color = 'red';
        if (changeIcon) {
            icon = 'close';
        }
    } else if (warnings > 0) {
        color = 'orange';
        if (changeIcon) {
            icon = 'exclamation circle';
        }
    } else if (errors > 0) {
        color = 'red';
        if (changeIcon) {
            icon = 'exclamation circle';
        }
    }
    return (<span data-tooltip="Show Info" data-inverted="">
        <Icon loading={spinning}
              name={icon}
              color={color}
              title={'Show info'}
              onClick={segmentInfoClickHandler}/>
    </span>)
};

export default LogSegmentV3;
