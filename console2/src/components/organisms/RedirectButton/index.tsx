import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Button } from 'semantic-ui-react';
import { ButtonProps } from 'semantic-ui-react/dist/commonjs/elements/Button/Button';

interface ExternalProps extends ButtonProps {
    location: string;
}

interface DispatchProps {
    redirect: () => void;
}

class RedirectButton extends React.PureComponent<ExternalProps & DispatchProps> {
    render() {
        const { redirect, location, ...rest } = this.props;
        return <Button {...rest} onClick={() => redirect()} />;
    }
}

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { location }: ExternalProps
): DispatchProps => ({
    redirect: () => dispatch(pushHistory(location))
});

export default connect(null, mapDispatchToProps)(RedirectButton);
