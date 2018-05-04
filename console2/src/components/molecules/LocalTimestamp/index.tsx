import * as React from 'react';

import { formatTimestamp } from '../../../utils';

interface Props {
    value?: string;
}

export default class extends React.PureComponent<Props> {
    render() {
        const { value } = this.props;

        if (!value) {
            return <div>Bad date value provided</div>;
        }

        return formatTimestamp(value);
    }
}
