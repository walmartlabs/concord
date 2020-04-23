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

import './styles.css';
import { Button, Icon } from 'semantic-ui-react';
import { LogRange, SegmentStatus } from '../../../api/process/log';
import { Link } from 'react-router-dom';
import { ConcordId } from '../../../api/common';

interface Props {
    instanceId: ConcordId;
    segmentId: number;
    name: string;
    status: SegmentStatus;
    range: LogRange;
    data: string[];
    onStartLoading: (isLoadWholeLog: boolean) => void;
    onStopLoading: () => void;
    onSegmentInfo?: () => void;
    loading: boolean;
    open: boolean;
}

const LogSegment = ({
    instanceId,
    segmentId,
    name,
    status,
    range,
    data,
    onStartLoading,
    onStopLoading,
    onSegmentInfo,
    loading,
    open
}: Props) => {
    const scrollAnchorRef = useRef<HTMLDivElement>(null);

    const [isOpen, setOpen] = useState<boolean>(open);
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

    return (
        <div className="LogSegment">
            <Button
                fluid={true}
                size="medium"
                className="Segment"
                onClick={() => setOpen((prevState) => !prevState)}>
                <Icon name={isOpen ? 'caret down' : 'caret right'} className="State" />
                <StatusIcon status={status} />
                <span className="Caption">{name}</span>

                <Link
                    to={`/api/v2/process/${instanceId}/log/segment/${segmentId}/data`}
                    onClick={(event) => event.stopPropagation()}
                    target="_blank"
                    title="Pop out"
                    className="AdditionalAction Last">
                    <Icon name="external alternate" />
                </Link>

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

                {loading && (
                    <div className="AdditionalAction">
                        <Icon loading={loading} name="spinner" />
                    </div>
                )}
            </Button>

            {isOpen && (
                <div className="ContentContainer">
                    <div className="InnerContentContainer">
                        <div className="Content">
                            {range.low !== 0 && (
                                <>
                                    <span>...showing only the last few lines... </span>
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
    status: SegmentStatus;
}

const StatusIcon = ({ status }: StatusIconProps) => {
    switch (status) {
        case SegmentStatus.OK:
            return <Icon name="check circle" color="green" className="Status" />;
        case SegmentStatus.FAILED:
            return <Icon name="close" color="red" className="Status" />;
        case SegmentStatus.RUNNING:
            return <Icon loading={true} name="spinner" color="grey" className="Status" />;
        case undefined:
            return <span className="EmptyStatus"> </span>;
    }
};

export default LogSegment;
