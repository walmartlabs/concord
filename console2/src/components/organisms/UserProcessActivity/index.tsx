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

import {
    getActivity as apiGetActivity,
    listProcessCards as apiListProcessCards, ProcessCardEntry
} from '../../../api/service/console/user';
import { Button, Card, CardGroup, Header, Icon, Image, Modal } from 'semantic-ui-react';
import { ProcessList } from '../../molecules/index';
import { ProcessEntry } from '../../../api/process';
import {
    CREATED_AT_COLUMN,
    INITIATOR_COLUMN,
    REPO_COLUMN,
    INSTANCE_ID_COLUMN,
    PROJECT_COLUMN,
    STATUS_COLUMN
} from '../../molecules/ProcessList';
import { useCallback, useEffect, useState } from 'react';
import { useApi } from '../../../hooks/useApi';
import { LoadingDispatch } from '../../../App';
import RequestErrorActivity from '../RequestErrorActivity';
import {Link} from "react-router-dom";
import './styles.css';

export interface ExternalProps {
    forceRefresh: any;
}

const MAX_OWN_PROCESSES = 10;

const renderCard = (card: ProcessCardEntry) => {
    return (
        <Card key={card.id}>
            <Card.Content>
                {card.icon && <Image floated='right' size='mini' src={`data:image/png;base64, ${card.icon}`}/>}
                <Card.Header>{card.name}</Card.Header>

                <Card.Meta>
                    Project: <Link to={`/org/${card.orgName}/project/${card.projectName}`}>{card.projectName}</Link>
                </Card.Meta>

                <Card.Description>{card.description}</Card.Description>
            </Card.Content>

            <Card.Content extra>
                <div className='ui two buttons'>
                    {card.isCustomForm &&
                        <Modal trigger={<Button basic color='green'>Start process</Button>}>
                            <Modal.Content>
                                <div className={"ui active embed"}>
                                    <iframe title={card.id}
                                            src={`/api/v1/processcard/${card.id}/form`}
                                            height={"100%"}
                                            width={"100%"}
                                            frameBorder={0}
                                            allowFullScreen={true}/>
                                </div>
                            </Modal.Content>
                        </Modal>
                    }

                    {!card.isCustomForm &&
                        <Button basic color='green' href={`/api/v1/org/${card.orgName}/project/${card.projectName}/repo/${card.repoName}/start/${card.entryPoint}`} target="_blank" rel="noopener noreferrer">Start process</Button>
                    }
                </div>
            </Card.Content>
        </Card>
    );
};

const renderCards = (cards: ProcessCardEntry[]) => {
    if (cards.length === 0) {
        return;
    }

    return (
        <CardGroup>
            {cards.map((card) => renderCard(card))}
        </CardGroup>
    );
};

interface ActivityEntry {
    processes?: ProcessEntry[];
    cards?: ProcessCardEntry[];
}

const UserProcesses = ({ forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const [processes, setProcesses] = useState<ProcessEntry[]>();
    const [processCards, setProcessCards] = useState<ProcessCardEntry[]>();
    const [processCardsShow, toggleProcessCardsShow] = useState<boolean>(true);

    const processCardsShowHandler = useCallback(() => {
        toggleProcessCardsShow((prevState) => !prevState);
    }, []);

    const fetchData = useCallback(async () => {
        const activity = await apiGetActivity(MAX_OWN_PROCESSES);
        const cards = await apiListProcessCards();
        return { processes: activity.processes, cards}
    }, []);

    const { data, error } = useApi<ActivityEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    useEffect(() => {
        if (!data) {
            return;
        }

        setProcesses(data.processes);
        setProcessCards(data.cards)
    }, [data]);

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            {processCards && processCards.length > 0 &&
                <Header dividing={true} as="h3" className={"no-margin-top"}>
                    Actions
                    <Icon link={true} name={processCardsShow ? "angle down" : "angle left"} onClick={processCardsShowHandler} style={{fontSize: '1em', verticalAlign: 'top'}}></Icon>
                </Header>
            }

            {processCardsShow && processCards && renderCards(processCards)}

            <Header dividing={true} as="h3">
                Your last {MAX_OWN_PROCESSES} processes
            </Header>
            <ProcessList
                data={processes}
                columns={[
                    STATUS_COLUMN,
                    INSTANCE_ID_COLUMN,
                    PROJECT_COLUMN,
                    REPO_COLUMN,
                    INITIATOR_COLUMN,
                    CREATED_AT_COLUMN
                ]}
            />
        </>
    );
};

export default UserProcesses;
