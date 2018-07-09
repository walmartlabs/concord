import * as React from 'react';

interface HighlighterProps {
    config: Config[];
    value: string;
    caseInsensitive?: boolean;
    global?: boolean;
}

interface Config {
    string: string;
    style: string;
}

class Highlighter extends React.PureComponent<HighlighterProps> {
    constructor(props: HighlighterProps) {
        super(props);
    }

    render() {
        const { config, value, caseInsensitive = false, global = true } = this.props;
        const regExpCfg = `${caseInsensitive ? 'i' : ''}
            ${global ? 'g' : ''}`.trim();
        let txt = value;

        for (const cfg of config) {
            txt = txt.replace(
                RegExp(cfg.string, regExpCfg),
                () => `<span style="${cfg.style}"><b>${cfg.string}</b></span>`
            );
        }
        return <div dangerouslySetInnerHTML={{ __html: txt }} />;
    }
}
export default Highlighter;
