import * as React from 'react';
import { Breadcrumb, Segment } from 'semantic-ui-react';
import { OrganizationList } from '../../organisms';

export default class extends React.PureComponent {
    render() {
        return (
            <>
                <Segment basic={true}>
                    <Breadcrumb size="big">
                        <Breadcrumb.Section active={true}>Organizations</Breadcrumb.Section>
                    </Breadcrumb>
                </Segment>

                <OrganizationList />
            </>
        );
    }
}
