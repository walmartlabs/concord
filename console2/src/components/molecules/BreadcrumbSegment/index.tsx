import * as React from 'react';
import { Breadcrumb, Segment } from 'semantic-ui-react';

import './styles.css';

class BreadcrumbSegment extends React.PureComponent {
    render() {
        return (
            <Segment basic={true} className="breadcrumbSegment">
                <Breadcrumb size="big">{this.props.children}</Breadcrumb>
            </Segment>
        );
    }
}

export default BreadcrumbSegment;
