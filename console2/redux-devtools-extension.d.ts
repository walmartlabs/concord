// TODO: Remove once this is merged https://github.com/zalmoxisus/redux-devtools-extension/pull/493
import {StoreEnhancer} from 'redux';

declare module 'redux-devtools-extension' {
    export function composeWithDevTools(...fincs: Function[]): StoreEnhancer;
    export function devToolsEnhancer(options: any): StoreEnhancer;
}