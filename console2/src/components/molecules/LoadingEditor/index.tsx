import * as React from 'react';

import { Loader } from 'semantic-ui-react';
import Editor from '@monaco-editor/react';

interface LoadingEditorProps {
    language?: string;
    handleEditorDidMount: (getEditorValue: () => string) => void;
    initValue?: string;
    disabled: boolean;
}

const LoadingEditor = ({
    language,
    handleEditorDidMount,
    initValue,
    disabled
}: LoadingEditorProps) => {
    if (!initValue) {
        return <Loader active={true} />;
    }

    return (
        <Editor
            language={language}
            editorDidMount={handleEditorDidMount}
            value={initValue}
            options={{ lineNumbers: 'on', minimap: { enabled: false }, readOnly: disabled }}
        />
    );
};

export default LoadingEditor;
