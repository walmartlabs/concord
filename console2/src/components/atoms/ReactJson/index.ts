import type { ComponentType } from 'react';
import ReactJsonModule, { type ReactJsonViewProps } from 'react-json-view';

type ReactJsonModuleShape = ComponentType<ReactJsonViewProps> & {
    default?: ComponentType<ReactJsonViewProps>;
};

// Vite 8 can surface this UMD dependency as either the component or a module object.
const ReactJson =
    (ReactJsonModule as ReactJsonModuleShape).default ??
    (ReactJsonModule as ReactJsonModuleShape);

export default ReactJson;
