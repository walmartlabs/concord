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

import './styles.css';
import {Button, Icon} from "semantic-ui-react";
import {Link} from "react-router-dom";
import {useState} from "react";
import {useEffect} from "react";

export type SegmentStatus = 'ok' | 'error' | 'running' | undefined;

interface ExternalProps {
    correlationId: string;
    name: string;
    status: SegmentStatus;
    data: string[];
    startLoading: (correlationId: string, name: string) => void;
    stopLoading: (correlationId: string, name: string) => void;
}

const LogSegment = ({correlationId, name, status, data, startLoading, stopLoading}: ExternalProps) => {
    const [loading, setLoading] = useState<boolean>(false);
    const [isOpen, setOpen] = useState<boolean>(false);

    useEffect(() => {
        if (isOpen) {
            startLoading(correlationId, name);
            setLoading(true);
        } else {
            stopLoading(correlationId, name);
            setLoading(false);
        }
    }, [isOpen]);

    return (
        <div className="LogSegment">
            <Button fluid={true} size={"medium"} className="Segment" onClick={event => setOpen((prevState) => !prevState)}>
                <Icon name={isOpen ? 'caret down' : 'caret right'} className="State"/>
                <StatusIcon status={status}/>
                <span className="Caption">{name}</span>

                <Link to={"#"} title={"Open Raw Step Output in New Tab"} className="AdditionalAction">
                    <Icon name='external alternate'/>
                </Link>
            </Button>

            {isOpen && loading && data.length === 0 &&
                <div className="ContentContainer">
                    <div className="Loading">
                        Loading
                    </div>
                </div>
            }

            {isOpen && data.length > 0 &&
                <div className="ContentContainer">
                    <div className="InnerContentContainer">
                        <div className="Content">
                            <pre>
                                {"15:10:37 [INFO ] Copying the repository's data: RepositoryEntry{id=f2c25718-7b23-11ea-9a22-0242ac110002, projectId=ccf96f92-7b20-11ea-b2dc-0242ac110002, name='test-error', url='https://gecgithub01.walmart.com/vn0tj0b/concord-hello-world.git', branch='null', commitId='null', path='null', secretId=null, secretName='null', secretStoreType='null', disabled=false, meta=null}\n"}
                                {"15:10:40 [INFO ] Using entry point: default\n"}
                                {"15:10:40 [INFO ] Storing default dependency versions...\n"}
                                {"15:10:40 [INFO ] Enqueued. Waiting for an agent (requirements=null)...\n"}
                            </pre>
                        </div>
                    </div>
                </div>
            }
        </div>
        );
};

interface StatusIconProps {
    status: SegmentStatus;
}

const StatusIcon = ({status} : StatusIconProps) => {
    switch (status) {
        case 'ok':
            return (
                <Icon name="check circle" color="green" className="Status"/>
            );
        case 'error':
            return (
                <Icon name="close" color="red" className="Status"/>
            );
        case 'running':
            return (
                <Icon loading={true} name="spinner" color="grey" className="Status"/>
            );
        case undefined:
            return (<span className="EmptyStatus"> </span>);
    }
};

export default LogSegment;
