/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import {RouteComponentProps, withRouter} from 'react-router';
import {Button, Divider, Header, Image, Loader} from "semantic-ui-react";
import {getProcessCard as apiGetProcessCard, ProcessCardEntry} from "../../../api/service/console/user";
import {useApi} from "../../../hooks/useApi";
import {RequestErrorMessage} from "../../molecules";
import {useCallback, useContext} from "react";
import {UserSessionContext} from "../../../session";

interface RouteProps {
    cardId: string;
}

function ProcessCardFormPage(props: RouteComponentProps<RouteProps>) {
    const {cardId} = props.match.params;

    const { userInfo } = useContext(UserSessionContext);

    const getProcessCard = useCallback(() => apiGetProcessCard(cardId), [cardId]);

    const {data: card, error, isLoading} = useApi<ProcessCardEntry>(getProcessCard, {
        fetchOnMount: true
    });

    if (error) {
        return <RequestErrorMessage error={error}/>;
    }


    if (!card || isLoading) {
        return <Loader active={true}/>;
    }

    return <>
        <div style={{margin: "24px"}}>
            <div style={{display: "flex", flexDirection: "row"}}>
                <div style={{flexGrow: 1}}>
                <Header as="h2">{card.icon && <Image size='mini' src={`data:image/png;base64, ${card.icon}`}/>}{card.name}</Header>
                <Header as="h5">{card.description}</Header>
                </div>
                <div>
                    Logged in as: <b>{userInfo?.displayName ? `${userInfo.displayName} (${userInfo.username})` : userInfo?.username}</b>
                </div>
            </div>
            <Divider/>
            {card.isCustomForm &&
                <div className={"ui active embed"}>
                    <iframe title={card.id}
                            src={`/api/v1/processcard/${card.id}/form`}
                            height={"100%"}
                            width={"100%"}
                            allowFullScreen={true}/>
                </div>
            }

            {!card.isCustomForm &&
                <Button basic color='green'
                        href={`/api/v1/org/${card.orgName}/project/${card.projectName}/repo/${card.repoName}/start/${card.entryPoint}`}>Start</Button>
            }

        </div>
    </>;
}

export default withRouter(ProcessCardFormPage);
