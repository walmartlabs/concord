// @flow
import {sort} from "../../constants";

export const columns = [
    {key: "name", label: "Name", collapsing: true},
    {key: "type", label: "Type"},
    {key: "actions", label: "Actions", collapsing: true}
];

export const nameKey = "name";
export const typeKey = "type";
export const sortableKeys = ["name"];
export const actionsKey = "actions";

export const defaultSortKey = "name";
export const defaultSortDir = sort.ASC;
