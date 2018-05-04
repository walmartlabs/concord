import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import { Grid } from 'semantic-ui-react';

import { TopBar } from '../../organisms';

type Props = RouteComponentProps<{}>;

class Layout extends React.PureComponent<Props> {
    render() {
        // TODO is there a better way?
        const fullScreen = this.props.location.search.search('fullScreen=true') >= 0;

        return (
            <Grid centered={true}>
                {!fullScreen && (
                    <Grid.Column width={16}>
                        <TopBar />
                    </Grid.Column>
                )}
                <Grid.Column width={11}>{this.props.children}</Grid.Column>
            </Grid>
        );
    }
}

export default withRouter(Layout);
