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
import { RouteComponentProps, withRouter } from 'react-router';
import { Breadcrumb } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';

interface RouteProps {
    resourceName: string;
}

type Props = RouteComponentProps<RouteProps>;

const CustomResourcePage = (props: Props) => {
    const key = props.match.params.resourceName;
    const resource = window.concord.customResources && window.concord.customResources[key];
    if (!resource) {
        return <p>'Not available.'</p>;
    }

    return (
        <>
            <BreadcrumbSegment>
                <Breadcrumb.Section>{resource?.title || key}</Breadcrumb.Section>
            </BreadcrumbSegment>

            {resource?.description && (
                <p>Number of free workers for different "flavors" of Agents.</p>
            )}

            <iframe
                title="Extenal Resource"
                src={resource.url}
                width={resource.width || '100%'}
                height={resource.height || '500'}
                frameBorder="0"
            />
        </>
    );
};

export default withRouter(CustomResourcePage);
