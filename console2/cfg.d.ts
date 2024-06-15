import {ColumnDefinition} from "./src/api/org";

export {};

export interface ConcordEnvironment {
    topBar?: TopBarMeta;
    loginUrl?: string;
    logoutUrl?: string;
    login?: LoginConfiguration;
    extraProcessMenuLinks?: ExtraProcessMenuLinks;
    lastUpdated?: string;
    customResources?: CustomResources;
    processListColumns?: ColumnDefinition[];
}

export interface TopBarMeta {
    systemLinks?: SystemLinks;
}

export type SystemLinks = LinkMeta[];

export type ExtraProcessMenuLinks = ExtraProcessMenuLink[];

export interface LinkMeta {
    text: string;
    url: string;
    icon?: string;
}

export interface LoginConfiguration {
    usernameValidator?: (username: string) => string | undefined;
    usernameHint?: string;
}

export interface ExtraProcessMenuLink {
    url: string;
    label: string;
    color: string;
    icon: string;
}

export interface CustomResources {
    [key: string]: CustomResource;
}

export interface CustomResource {
    title?: string;
    description?: string;
    icon?: string;
    url: string;
    width?: string;
    height?: string;
}

declare global {
    interface Window {
        concord: ConcordEnvironment;
    }
}
